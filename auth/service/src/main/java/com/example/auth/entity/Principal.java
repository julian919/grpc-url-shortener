package com.example.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The identity root. {@code roles} and {@code permissions} are plain Postgres {@code text[]}
 * columns, string-matched by name against the {@link Role} catalog at token-mint time -- no
 * join table, no foreign key. {@code permissions} is a direct-grant escape hatch alongside
 * {@code roles}, kept separate so a one-off grant never requires inventing a one-person role.
 */
@Entity
@Table(name = "principals")
public class Principal {

  @Id private UUID id;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "roles", columnDefinition = "text[]")
  private Set<String> roles = new HashSet<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "permissions", columnDefinition = "text[]")
  private Set<String> permissions = new HashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Principal() {
    // JPA
  }

  public Principal(String secretHash, Set<String> roles) {
    // Assigned, not @GeneratedValue -- a valid id from the moment of construction, not only
    // after Hibernate persists it, which is what makes this entity usable in a plain unit
    // test with no persistence context at all.
    this.id = UUID.randomUUID();
    this.secretHash = secretHash;
    this.roles = new HashSet<>(roles);
  }

  public UUID getId() {
    return id;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
