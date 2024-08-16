package io.unitycatalog.server.service;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.*;
import io.unitycatalog.server.auth.UnityCatalogAuthorizer;
import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import io.unitycatalog.server.exception.GlobalExceptionHandler;
import io.unitycatalog.server.model.CatalogInfo;
import io.unitycatalog.server.model.Privilege;
import io.unitycatalog.server.model.PrivilegeAssignment;
import io.unitycatalog.server.model.ResourceType;
import io.unitycatalog.server.model.SchemaInfo;
import io.unitycatalog.server.model.TableInfo;
import io.unitycatalog.server.model.UpdateAuthorizationChange;
import io.unitycatalog.server.model.UpdateAuthorizationRequest;
import io.unitycatalog.server.model.UpdateAuthorizationResponse;
import io.unitycatalog.server.model.User;
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

  @Get("/{resource_type}/{name}")
  public HttpResponse getAuthorization(
      @Param("resource_type") ResourceType resourceType,
      @Param("name") String name,
      @Param("principal") Optional<String> principal) {
    UUID resourceId = getResourceId(resourceType, name);
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

    return HttpResponse.ofJson(
        new UpdateAuthorizationResponse().privilegeAssignments(privilegeAssignments));
  }

  @Patch("/{resource_type}/{name}")
  public HttpResponse updateAuthorization(
      @Param("resource_type") ResourceType resourceType,
      @Param("name") String name,
      UpdateAuthorizationRequest request) {
    UUID resourceId = getResourceId(resourceType, name);
    List<UpdateAuthorizationChange> changes = request.getChanges();
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
                        .principal(entry.getKey().toString())
                        .privileges(entry.getValue()))
            .collect(Collectors.toList());

    return HttpResponse.ofJson(
        new UpdateAuthorizationResponse().privilegeAssignments(privilegeAssignments));
  }

  private UUID getResourceId(ResourceType resourceType, String name) {
    UUID resourceId;

    if (resourceType.equals(ResourceType.METASTORE)) {
      resourceId = METASTORE_REPOSITORY.getMetastoreId();
    } else if (resourceType.equals(ResourceType.CATALOG)) {
      CatalogInfo catalogInfo = CATALOG_REPOSITORY.getCatalog(name);
      resourceId = UUID.fromString(Objects.requireNonNull(catalogInfo.getId()));
    } else if (resourceType.equals(ResourceType.SCHEMA)) {
      SchemaInfo schemaInfo = SCHEMA_REPOSITORY.getSchema(name);
      resourceId = UUID.fromString(Objects.requireNonNull(schemaInfo.getSchemaId()));
    } else if (resourceType.equals(ResourceType.TABLE)) {
      TableInfo tableInfo = TABLE_REPOSITORY.getTable(name);
      resourceId = UUID.fromString(Objects.requireNonNull(tableInfo.getTableId()));
    } else {
      throw new BaseException(ErrorCode.FAILED_PRECONDITION, "Unknown resource type");
    }
    return resourceId;
  }
}
