package io.unitycatalog.server.service;

import static io.unitycatalog.server.model.SecurableType.CATALOG;
import static io.unitycatalog.server.model.SecurableType.FUNCTION;
import static io.unitycatalog.server.model.SecurableType.METASTORE;
import static io.unitycatalog.server.model.SecurableType.SCHEMA;
import static io.unitycatalog.server.model.SecurableType.TABLE;
import static io.unitycatalog.server.model.SecurableType.VOLUME;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.*;
import io.unitycatalog.control.model.User;
import io.unitycatalog.server.auth.UnityCatalogAuthorizer;
import io.unitycatalog.server.auth.annotation.AuthorizeExpression;
import io.unitycatalog.server.auth.annotation.AuthorizeKey;
import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import io.unitycatalog.server.exception.GlobalExceptionHandler;
import io.unitycatalog.server.model.CatalogInfo;
import io.unitycatalog.server.model.PermissionsChange;
import io.unitycatalog.server.model.PermissionsList;
import io.unitycatalog.server.model.Privilege;
import io.unitycatalog.server.model.PrivilegeAssignment;
import io.unitycatalog.server.model.SchemaInfo;
import io.unitycatalog.server.model.SecurableType;
import io.unitycatalog.server.model.TableInfo;
import io.unitycatalog.server.model.UpdatePermissions;
import io.unitycatalog.server.persist.CatalogRepository;
import io.unitycatalog.server.persist.MetastoreRepository;
import io.unitycatalog.server.persist.SchemaRepository;
import io.unitycatalog.server.persist.TableRepository;
import io.unitycatalog.server.persist.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ExceptionHandler(GlobalExceptionHandler.class)
public class PermissionService {

  private final UnityCatalogAuthorizer authorizer;
  private static final MetastoreRepository METASTORE_REPOSITORY = MetastoreRepository.getInstance();
  private static final UserRepository USER_REPOSITORY = UserRepository.getInstance();
  private static final CatalogRepository CATALOG_REPOSITORY = CatalogRepository.getInstance();
  private static final SchemaRepository SCHEMA_REPOSITORY = SchemaRepository.getInstance();
  private static final TableRepository TABLE_REPOSITORY = TableRepository.getInstance();

  public PermissionService(UnityCatalogAuthorizer authorizer) {
    this.authorizer = authorizer;
  }

  // TODO: Refactor these endpoints to use a common method with dynamic resource id lookup
  @Get("/metastore/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #metastore, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getMetastoreAuthorization(
      @Param("name") String name, @Param("principal") Optional<String> principal) {
    return getAuthorization(METASTORE, name, principal);
  }

  @Get("/catalog/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #catalog, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getCatalogAuthorization(
      @Param("name") @AuthorizeKey(CATALOG) String name,
      @Param("principal") Optional<String> principal) {
    return getAuthorization(METASTORE, name, principal);
  }

  @Get("/schema/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #schema, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getSchemaAuthorization(
      @Param("name") @AuthorizeKey(SCHEMA) String name,
      @Param("principal") Optional<String> principal) {
    return getAuthorization(SCHEMA, name, principal);
  }

  @Get("/table/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #table, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getTableAuthorization(
      @Param("name") @AuthorizeKey(TABLE) String name,
      @Param("principal") Optional<String> principal) {
    return getAuthorization(TABLE, name, principal);
  }

  @Get("/function/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #function, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getFunctionAuthorization(
      @Param("name") @AuthorizeKey(FUNCTION) String name,
      @Param("principal") Optional<String> principal) {
    return getAuthorization(FUNCTION, name, principal);
  }

  @Get("/volume/{name}")
  @AuthorizeExpression("#authorizeAny(#principal, #volume, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse getVolumeAuthorization(
      @Param("name") @AuthorizeKey(VOLUME) String name,
      @Param("principal") Optional<String> principal) {
    return getAuthorization(VOLUME, name, principal);
  }

  private HttpResponse getAuthorization(
      SecurableType securableType, String name, Optional<String> principal) {
    UUID resourceId = getResourceId(securableType, name);
    Map<UUID, List<Privilege>> authorizations;
    if (principal.isPresent()) {
      User user = USER_REPOSITORY.getUserByEmail(principal.get());
      UUID principalId = UUID.fromString(Objects.requireNonNull(user.getId()));
      authorizations = Map.of(principalId, authorizer.listAuthorizations(principalId, resourceId));
    } else {
      authorizations = authorizer.listAuthorizations(resourceId);
    }

    List<PrivilegeAssignment> privilegeAssignments =
        authorizations.entrySet().stream()
            .map(
                entry ->
                    new PrivilegeAssignment()
                        .principal(USER_REPOSITORY.getUser(entry.getKey().toString()).getEmail())
                        .privileges(entry.getValue()))
            .collect(Collectors.toList());

    return HttpResponse.ofJson(new PermissionsList().privilegeAssignments(privilegeAssignments));
  }

  // TODO: Refactor these endpoints to use a common method with dynamic resource id lookup
  @Patch("/metastore/{name}")
  @AuthorizeExpression("#authorize(#principal, #metastore, METASTORE_ADMIN)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateMetastoreAuthorization(
      @Param("name") String name, UpdatePermissions request) {
    return updateAuthorization(METASTORE, name, request);
  }

  @Patch("/catalog/{name}")
  @AuthorizeExpression(
      "#authorize(#principal, #metastore, METASTORE_ADMIN) || #authorize(#principal, #catalog, OWNER)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateCatalogAuthorization(
      @Param("name") @AuthorizeKey(CATALOG) String name, UpdatePermissions request) {
    return updateAuthorization(CATALOG, name, request);
  }

  @Patch("/schema/{name}")
  @AuthorizeExpression(
      "#authorize(#principal, #metastore, METASTORE_ADMIN) || #authorize(#principal, #schema, OWNER)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateSchemaAuthorization(
      @Param("name") @AuthorizeKey(SCHEMA) String name, UpdatePermissions request) {
    return updateAuthorization(SCHEMA, name, request);
  }

  @Patch("/table/{name}")
  @AuthorizeExpression(
      "#authorize(#principal, #metastore, METASTORE_ADMIN) || #authorize(#principal, #table, OWNER)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateTableAuthorization(
      @Param("name") @AuthorizeKey(TABLE) String name, UpdatePermissions request) {
    return updateAuthorization(TABLE, name, request);
  }

  @Patch("/function/{name}")
  @AuthorizeExpression(
      "#authorize(#principal, #metastore, METASTORE_ADMIN) || #authorize(#principal, #function, OWNER)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateFunctionAuthorization(
      @Param("name") @AuthorizeKey(FUNCTION) String name, UpdatePermissions request) {
    return updateAuthorization(FUNCTION, name, request);
  }

  @Patch("/volume/{name}")
  @AuthorizeExpression(
      "#authorize(#principal, #metastore, METASTORE_ADMIN) || #authorize(#principal, #volume, OWNER)")
  @AuthorizeKey(METASTORE)
  public HttpResponse updateVolumeAuthorization(
      @Param("name") @AuthorizeKey(VOLUME) String name, UpdatePermissions request) {
    return updateAuthorization(VOLUME, name, request);
  }

  private HttpResponse updateAuthorization(
      SecurableType securableType, String name, UpdatePermissions request) {
    UUID resourceId = getResourceId(securableType, name);
    List<PermissionsChange> changes = request.getChanges();
    Set<UUID> principalIds = new HashSet<>();
    changes.forEach(
        change -> {
          String principal = change.getPrincipal();
          User user = USER_REPOSITORY.getUserByEmail(principal);
          UUID principalId = UUID.fromString(Objects.requireNonNull(user.getId()));
          principalIds.add(principalId);
          change
              .getAdd()
              .forEach(
                  privilege -> authorizer.grantAuthorization(principalId, resourceId, privilege));
          change
              .getRemove()
              .forEach(
                  privilege -> authorizer.revokeAuthorization(principalId, resourceId, privilege));
        });

    Map<UUID, List<Privilege>> authorizations = authorizer.listAuthorizations(resourceId);
    List<PrivilegeAssignment> privilegeAssignments =
        authorizations.entrySet().stream()
            .filter(entry -> principalIds.contains(entry.getKey()))
            .map(
                entry ->
                    new PrivilegeAssignment()
                        .principal(USER_REPOSITORY.getUser(entry.getKey().toString()).getEmail())
                        .privileges(entry.getValue()))
            .collect(Collectors.toList());

    return HttpResponse.ofJson(new PermissionsList().privilegeAssignments(privilegeAssignments));
  }

  private UUID getResourceId(SecurableType securableType, String name) {
    UUID resourceId;

    if (securableType.equals(METASTORE)) {
      resourceId = METASTORE_REPOSITORY.getMetastoreId();
    } else if (securableType.equals(SecurableType.CATALOG)) {
      CatalogInfo catalogInfo = CATALOG_REPOSITORY.getCatalog(name);
      resourceId = UUID.fromString(Objects.requireNonNull(catalogInfo.getId()));
    } else if (securableType.equals(SecurableType.SCHEMA)) {
      SchemaInfo schemaInfo = SCHEMA_REPOSITORY.getSchema(name);
      resourceId = UUID.fromString(Objects.requireNonNull(schemaInfo.getSchemaId()));
    } else if (securableType.equals(SecurableType.TABLE)) {
      TableInfo tableInfo = TABLE_REPOSITORY.getTable(name);
      resourceId = UUID.fromString(Objects.requireNonNull(tableInfo.getTableId()));
    } else {
      throw new BaseException(ErrorCode.FAILED_PRECONDITION, "Unknown resource type");
    }
    return resourceId;
  }
}
