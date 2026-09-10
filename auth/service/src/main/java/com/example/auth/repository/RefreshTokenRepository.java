package com.example.auth.repository;

import com.example.auth.user.PrincipalEntity;
import com.example.auth.user.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Modifying
  @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.principal = :principal")
  void revokeAllForPrincipal(@Param("principal") PrincipalEntity principal);
}
