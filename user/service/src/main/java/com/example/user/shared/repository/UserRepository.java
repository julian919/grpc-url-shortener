package com.example.user.shared.repository;

import com.example.user.shared.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Shared data access for the users table. Lives at the service root rather than inside a feature
 * package because more than one feature needs it -- registration writes a profile, and the user
 * lookup reads one.
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

  /**
   * The lookup every caller outside this service actually needs: a JWT carries the principal id in
   * {@code sub}, so that -- not this service's own id -- is how a user gets named across the wire.
   * Backed by the UNIQUE constraint on the column.
   */
  Optional<UserEntity> findByPrincipalId(UUID principalId);
}
