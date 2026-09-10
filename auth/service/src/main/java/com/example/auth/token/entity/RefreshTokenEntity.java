package com.example.auth.token.entity;

import com.example.auth.principal.entity.PrincipalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "principal_id", nullable = false)
  private PrincipalEntity principal;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  protected RefreshTokenEntity() {}

  public RefreshTokenEntity(
      UUID id,
      PrincipalEntity principal,
      String tokenHash,
      Instant expiresAt) {
    this.id = id;
    this.principal = principal;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.revoked = false;
  }

  public UUID getId() {
    return id;
  }

  public PrincipalEntity getPrincipal() {
    return principal;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public void revoke() {
    this.revoked = true;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
