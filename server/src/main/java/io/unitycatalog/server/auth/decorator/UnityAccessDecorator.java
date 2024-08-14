package io.unitycatalog.server.auth.decorator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.internal.server.annotation.AnnotatedService;
import com.linecorp.armeria.server.DecoratingHttpServiceFunction;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import com.linecorp.armeria.server.annotation.Param;
import io.unitycatalog.server.auth.UnityCatalogAuthorizer;
import io.unitycatalog.server.auth.annotation.AuthorizeExpression;
import io.unitycatalog.server.auth.annotation.AuthorizeKey;
import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import io.unitycatalog.server.model.CatalogInfo;
import io.unitycatalog.server.model.ResourceType;
import io.unitycatalog.server.model.SchemaInfo;
import io.unitycatalog.server.model.TableInfo;
import io.unitycatalog.server.persist.CatalogRepository;
import io.unitycatalog.server.persist.MetastoreRepository;
import io.unitycatalog.server.persist.SchemaRepository;
import io.unitycatalog.server.persist.TableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.unitycatalog.server.auth.decorator.KeyLocator.Source.PARAM;
import static io.unitycatalog.server.auth.decorator.KeyLocator.Source.PAYLOAD;
import static io.unitycatalog.server.auth.decorator.KeyLocator.Source.SYSTEM;
import static io.unitycatalog.server.model.ResourceType.CATALOG;
import static io.unitycatalog.server.model.ResourceType.METASTORE;
import static io.unitycatalog.server.model.ResourceType.SCHEMA;
import static io.unitycatalog.server.model.ResourceType.TABLE;

/**
 * Armeria access control Decorator.
 * <p>
 * This decorator provides the ability to protect Armeria service methods with per method access
 * control rules. This decorator is used in conjunction with two annotations, @AuthorizeExpression
 * and @AuthorizeKey to define authorization rules and identify requests parameters for objects
 * to authorize with.
 *
 * @AuthorizeExpression - This defines a Spring Expression Language expression to evaluate to make
 * an authorization decision.
 * @AuthorizeKey - This annotation is used to define request and payload parameters for the authorization
 * context. These are typically things like catalog, schema and table names. This annotation may be used
 * at both the method and method parameter context. It may be specified more than once per method to
 * map parameters to object keys.
 */
public class UnityAccessDecorator implements DecoratingHttpServiceFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnityAccessDecorator.class);

  private final UnityAccessEvaluator evaluator;

  public UnityAccessDecorator(UnityCatalogAuthorizer authorizer) throws NoSuchMethodException, IllegalAccessException {
    evaluator = new UnityAccessEvaluator(authorizer);
  }

  @Override
  public HttpResponse serve(HttpService delegate, ServiceRequestContext ctx, HttpRequest req)
          throws Exception {
    LOGGER.debug("AccessDecorator checking {}", req.path());

    Method method = findServiceMethod(ctx.config().service());

    if (method != null) {

      // Find the authorization parameters to use for this service method.
      String expression = findAuthorizeExpression(method);
      List<KeyLocator> locator = findAuthorizeKeys(method);

      if (expression != null) {
        if (!locator.isEmpty()) {

          UUID principal = UnityAccessUtil.findPrincipalId();

          if (locator.size() == 1 && locator.get(0).getSource().equals(SYSTEM)) {
            return authorizeBySystem(delegate, ctx, req, principal, locator.get(0).getType(), expression);
          } else {
            return authorizeByRequest(delegate, ctx, req, principal, locator, expression);
          }
        } else {
          LOGGER.warn("No authorization resource found.");
          throw new BaseException(ErrorCode.PERMISSION_DENIED, "Could not evaluate authorization.");
        }
      } else {
        LOGGER.debug("No authorization expression found.");
      }
    } else {
      LOGGER.warn("Couldn't unwrap service.");
    }

    return delegate.serve(ctx, req);
  }

  private HttpResponse authorizeBySystem(HttpService delegate, ServiceRequestContext ctx, HttpRequest req, UUID principal, ResourceType key, String expression) throws Exception {
    LOGGER.debug("resource: system = {}", key);

    Map<ResourceType, Object> resourceKeys = new HashMap<>();
    switch (key) {
      case METASTORE -> resourceKeys.put(METASTORE, "metastore");
    }

    checkAuthorization(principal, expression, resourceKeys);

    return delegate.serve(ctx, req);
  }

  private HttpResponse authorizeByRequest(HttpService delegate, ServiceRequestContext ctx, HttpRequest req, UUID principal, List<KeyLocator> locators, String expression) throws Exception {
    //
    // Based on the query and payload parameters defined on the service method (that
    // have been gathered as Locators), we'll attempt to find the entity/resource that
    // we want to authorize against.

    Map<ResourceType, Object> resourceKeys = new HashMap<>();

    // Split up the locators by type, because we have to extract the value from the request
    // different ways for different types

    List<KeyLocator> systemLocators = locators.stream().filter(l -> l.getSource().equals(SYSTEM)).toList();
    List<KeyLocator> paramLocators = locators.stream().filter(l -> l.getSource().equals(PARAM)).toList();
    List<KeyLocator> payloadLocators = locators.stream().filter(l -> l.getSource().equals(PAYLOAD)).toList();

    // Add system-type keys, just metastore for now.
    systemLocators.forEach(l -> resourceKeys.put(l.getType(), "metastore"));

    // Extract the query/path parameter values just by grabbing them from the request
    paramLocators.forEach(l -> {
      String value = ctx.pathParam(l.getKey()) != null ? ctx.pathParam(l.getKey()) : ctx.queryParam(l.getKey());
      resourceKeys.put(l.getType(), value);
    });

    if (payloadLocators.isEmpty()) {
      // If we don't have any PAYLOAD locators, we're ready to evaluate the authorization and allow or deny
      // the request.

      checkAuthorization(principal, expression, resourceKeys);

      return delegate.serve(ctx, req);
    } else {
      // Since we have PAYLOAD locators, we can only interrogate the payload while the request
      // is being evaluated, via peekData()

      // Note that peekData only gets called for requests that actually have data (like PUT and POST)

      var peekReq = req.peekData(data -> {
        try {
          // TODO: For now, we're going to assume JSON data, but might need to support other
          // content types.
          if (req.contentType().equals(MediaType.JSON)) {
            Map<String, Object> payload = new ObjectMapper().readValue(data.toReaderUtf8(), new TypeReference<>() {
            });

            payloadLocators.forEach(l -> resourceKeys.put(l.getType(), payload.get(l.getKey())));
          }
        } catch (Exception e) {
          // TODO: probably should fail the request
          LOGGER.warn("Error occurred while parsing payload:", e);
        }

        checkAuthorization(principal, expression, resourceKeys);
      });

      return delegate.serve(ctx, req);
    }
  }

  private void checkAuthorization(UUID principal, String expression, Map<ResourceType, Object> resourceKeys) {
    LOGGER.debug("resourceKeys = {}", resourceKeys);

    Map<ResourceType, Object> resourceIds = new HashMap<>();

    if (resourceKeys.containsKey(CATALOG) && resourceKeys.containsKey(SCHEMA) && resourceKeys.containsKey(TABLE)) {
      String fullName = resourceKeys.get(CATALOG) + "." + resourceKeys.get(SCHEMA) + "." + resourceKeys.get(TABLE);
      TableInfo table = TableRepository.getInstance().getTable(fullName);
      resourceIds.put(TABLE, UUID.fromString(table.getTableId()));
    }

    if (resourceKeys.containsKey(CATALOG) && resourceKeys.containsKey(SCHEMA)) {
      String fullName = resourceKeys.get(CATALOG) + "." + resourceKeys.get(SCHEMA);
      SchemaInfo schema = SchemaRepository.getInstance().getSchema(fullName);
      resourceIds.put(SCHEMA, UUID.fromString(schema.getSchemaId()));
    }

    if (resourceKeys.containsKey(CATALOG)) {
      String fullName = resourceKeys.get(CATALOG) + "";
      CatalogInfo catalog = CatalogRepository.getInstance().getCatalog(fullName);
      resourceIds.put(CATALOG, UUID.fromString(catalog.getId()));
    }

    if (resourceKeys.containsKey(METASTORE)) {
      resourceIds.put(METASTORE, MetastoreRepository.getInstance().getMetastoreId());
    }

    if (!resourceKeys.keySet().equals(resourceIds.keySet())) {
      LOGGER.warn("Some resource keys have unresolved ids.");
    }

    LOGGER.debug("resourceIds = {}", resourceIds);

    boolean allow = evaluator.evaluate(principal, expression, resourceIds);

    if (!allow) {
      throw new BaseException(ErrorCode.PERMISSION_DENIED, "Access denied.");
    }
  }

  private static String findAuthorizeExpression(Method method) {
    // TODO: Cache this by method

    AuthorizeExpression annotation = method.getAnnotation(AuthorizeExpression.class);

    if (annotation != null) {
      LOGGER.debug("authorize expression = {}", annotation.value());
      return annotation.value();
    } else {
      LOGGER.debug("authorize = (none found)");
      return null;
    }
  }

  private static List<KeyLocator> findAuthorizeKeys(Method method) {
    // TODO: Cache this by method

    List<KeyLocator> locators = new ArrayList<>();

    AuthorizeKey methodKey = method.getAnnotation(AuthorizeKey.class);

    // If resource is on the method, its source is from a global/system variable
    if (methodKey != null) {
      locators.add(KeyLocator.builder().source(SYSTEM).type(methodKey.value()).build());
    }

    for (Parameter parameter : method.getParameters()) {
      AuthorizeKey paramKey = parameter.getAnnotation(AuthorizeKey.class);

      if (paramKey != null) {
        if (!paramKey.key().isEmpty()) {
          // Explicitly declaring a key, so it's the source is from the payload data
          locators.add(KeyLocator.builder().source(PAYLOAD).type(paramKey.value()).key(paramKey.key()).build());
        } else {
          // No key defined so implicitly referencing an (annotated) (query) parameter
          Param param = parameter.getAnnotation(Param.class);
          if (param != null) {
            locators.add(KeyLocator.builder().source(PARAM).type(paramKey.value()).key(param.value()).build());
          } else {
            LOGGER.warn("Couldn't find param key for authorization key");
          }
        }
      }
    }
    return locators;
  }

  private static Method findServiceMethod(HttpService httpService) throws ClassNotFoundException {
    if (httpService.unwrap() instanceof SimpleDecoratingHttpService decoratingService &&
            decoratingService.unwrap() instanceof AnnotatedService service) {

      LOGGER.debug("serviceName = {}, methodName = {}", service.serviceName(), service.methodName());

      Class<?> clazz = Class.forName(service.serviceName());
      List<Method> methods = findMethodsByName(clazz, service.methodName());
      return (methods.size() == 1) ? methods.get(0) : null;
    } else {
      return null;
    }
  }

  private static List<Method> findMethodsByName(Class<?> clazz, String methodName) {
    List<Method> matchingMethods = new ArrayList<>();
    Method[] methods = clazz.getDeclaredMethods();

    for (Method method : methods) {
      if (method.getName().equals(methodName)) {
        matchingMethods.add(method);
      }
    }

    return matchingMethods;
  }

}
