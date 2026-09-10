package com.example.auth.token.repository;

import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.token.entity.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Modifying
  @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.principal = :principal")
  void revokeAllForPrincipal(@Param("principal") PrincipalEntity principal);
}
