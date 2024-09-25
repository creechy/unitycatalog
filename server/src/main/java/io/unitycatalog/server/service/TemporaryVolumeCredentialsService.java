package io.unitycatalog.server.service;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Post;
import io.unitycatalog.server.auth.annotation.AuthorizeExpression;
import io.unitycatalog.server.auth.annotation.AuthorizeKey;
import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import io.unitycatalog.server.exception.GlobalExceptionHandler;
import io.unitycatalog.server.model.GenerateTemporaryVolumeCredential;
import io.unitycatalog.server.model.VolumeInfo;
import io.unitycatalog.server.model.VolumeOperation;
import io.unitycatalog.server.persist.VolumeRepository;
import io.unitycatalog.server.service.credential.CredentialContext;
import io.unitycatalog.server.service.credential.CredentialOperations;

import java.util.Collections;
import java.util.Set;

import static io.unitycatalog.server.model.SecurableType.METASTORE;
import static io.unitycatalog.server.model.SecurableType.VOLUME;
import static io.unitycatalog.server.service.credential.CredentialContext.Privilege.SELECT;
import static io.unitycatalog.server.service.credential.CredentialContext.Privilege.UPDATE;

@ExceptionHandler(GlobalExceptionHandler.class)
public class TemporaryVolumeCredentialsService {

  private static final VolumeRepository VOLUME_REPOSITORY = VolumeRepository.getInstance();

  private final CredentialOperations credentialOps;

  public TemporaryVolumeCredentialsService(CredentialOperations credentialOps) {
    this.credentialOps = credentialOps;
  }

  @Post("")
  @AuthorizeExpression("""
            #authorize(#principal, #metastore, OWNER) ||
            #authorize(#principal, #catalog, OWNER) ||
            (#authorize(#principal, #schema, OWNER) && #authorize(#principal, #catalog, USE_CATALOG)) ||
            (#authorize(#principal, #schema, USE_SCHEMA) && #authorize(#principal, #catalog, USE_CATALOG) && #authorizeAny(#principal, #volume, OWNER, READ_VOLUME))
          """)
  @AuthorizeKey(METASTORE)
  public HttpResponse generateTemporaryTableCredential(
      @AuthorizeKey(value = VOLUME, key = "volume_id") GenerateTemporaryVolumeCredential generateTemporaryVolumeCredential) {
    String volumeId = generateTemporaryVolumeCredential.getVolumeId();
    if (volumeId.isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_ARGUMENT, "Volume ID is required.");
    }
    VolumeInfo volumeInfo = VOLUME_REPOSITORY.getVolumeById(volumeId);
    return HttpResponse.ofJson(
            credentialOps.vendCredential(
                    volumeInfo.getStorageLocation(),
                    volumeOperationToPrivileges(generateTemporaryVolumeCredential.getOperation())));
  }

  private Set<CredentialContext.Privilege> volumeOperationToPrivileges(VolumeOperation volumeOperation) {
    return switch (volumeOperation) {
      case READ_VOLUME -> Set.of(SELECT);
      case WRITE_VOLUME -> Set.of(SELECT, UPDATE);
      case UNKNOWN_VOLUME_OPERATION -> Collections.emptySet();
    };
  }
}
