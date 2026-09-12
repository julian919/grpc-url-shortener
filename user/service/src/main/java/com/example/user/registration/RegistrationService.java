package com.example.user.registration;

import com.example.user.exception.InvalidArgumentException;
import com.example.user.entity.UserEntity;
import com.example.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration spans two services: auth-service mints the identity, this one stores the profile.
 *
 * <p>Ordering matters and is deliberate. The principal is created FIRST, because its id is this
 * row's primary key -- there is no user without an identity. The failure mode that leaves behind
 * is a principal with no profile, which is recoverable (the profile can be backfilled). The
 * reverse order would produce a profile pointing at an identity that does not exist, which is not.
 *
 * <p>There is no distributed transaction here and deliberately so: the local @Transactional covers
 * only this service's write. Making the pair atomic would need an outbox or a saga, which is the
 * honest next step if orphaned principals ever become a real problem rather than a theoretical one.
 */
@Service
public class RegistrationService {

  private final PrincipalDirectory principalDirectory;
  private final UserRepository userRepository;

  public RegistrationService(PrincipalDirectory principalDirectory, UserRepository userRepository) {
    this.principalDirectory = principalDirectory;
    this.userRepository = userRepository;
  }

  @Transactional
  public UUID register(String email, String password, String firstName, String lastName) {
    if (email == null || email.isBlank()) {
      throw new InvalidArgumentException("email must not be blank");
    }
    if (password == null || password.isBlank()) {
      throw new InvalidArgumentException("password must not be blank");
    }
    if (firstName == null || firstName.isBlank()) {
      throw new InvalidArgumentException("first_name must not be blank");
    }
    if (lastName == null || lastName.isBlank()) {
      throw new InvalidArgumentException("last_name must not be blank");
    }

    UUID principalId = principalDirectory.createPrincipal(email, password);
    userRepository.save(new UserEntity(principalId, firstName, lastName, Instant.now()));
    return principalId;
  }
}
