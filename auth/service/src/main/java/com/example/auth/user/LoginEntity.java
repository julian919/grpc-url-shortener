package com.example.auth.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * One identifier pointing at a {@link PrincipalEntity} -- separated out so a
 * second
 * login method
 * (Google, mobile, ...) slots in as another row, not a schema change to
 * Principal itself.
 */
@Entity
@Table(name = "logins", uniqueConstraints = @UniqueConstraint(columnNames = { "provider", "account_id" }))
public class LoginEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "principal_id", nullable = false)
  private PrincipalEntity principal;

  @Enumerated(EnumType.STRING)
  private AuthProviderEnum provider;

  private String accountId;

  protected LoginEntity() {
    // JPA
  }

  public LoginEntity(PrincipalEntity principal, AuthProviderEnum provider, String accountId) {
    this.principal = principal;
    this.provider = provider;
    this.accountId = accountId;
  }

  public UUID getId() {
    return id;
  }

  public PrincipalEntity getPrincipal() {
    return principal;
  }

  public AuthProviderEnum getProvider() {
    return provider;
  }

  public String getAccountId() {
    return accountId;
  }
}
