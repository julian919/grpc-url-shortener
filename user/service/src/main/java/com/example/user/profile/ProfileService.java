package com.example.user.profile;

import com.example.user.shared.entity.UserEntity;
import com.example.user.shared.repository.UserRepository;
import io.grpc.Status;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads of the user profile. Registration writes it; this is the other half. */
@Service
public class ProfileService {

  private final UserRepository userRepository;

  public ProfileService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Looks a user up by the identity auth-service minted, which is what a JWT's {@code sub} carries
   * and therefore the only id a caller outside this service has.
   */
  @Transactional(readOnly = true)
  public UserEntity getByPrincipalId(String principalId) {
    UUID id;
    try {
      id = UUID.fromString(principalId);
    } catch (IllegalArgumentException notAUuid) {
      throw Status.INVALID_ARGUMENT
          .withDescription("principal_id must be a UUID (got: " + principalId + ")")
          .asRuntimeException();
    }

    return userRepository
        .findByPrincipalId(id)
        .orElseThrow(
            () ->
                Status.NOT_FOUND
                    .withDescription("no user for principal " + principalId)
                    .asRuntimeException());
  }
}
