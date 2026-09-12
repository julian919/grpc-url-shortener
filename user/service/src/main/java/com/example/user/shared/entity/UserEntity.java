package com.example.user.shared.entity;

import com.example.user.api.User;
import com.example.user.api.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The users table. Shared across features (registration writes one, the user lookup reads one), so
 * it sits at the service root alongside {@code repository/} rather than inside a feature package.
 *
 * <p>Its own generated id is the primary key; {@code principalId} references the identity
 * auth-service minted. That reference is NOT a foreign key -- principals live in another service's
 * database -- so the UNIQUE constraint on the column is what enforces one profile per identity.
 * Holds no credentials.
 */
@Entity
@Table(name = "users")
public class UserEntity {

  // Assigned, not @GeneratedValue -- same choice as PrincipalEntity: the row has a valid id from
  // the moment of construction, which is what makes it usable in a unit test with no persistence
  // context at all.
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  /**
   * The identity auth-service minted. A logical reference, not a foreign key -- principals live in
   * another service's database. UNIQUE in the schema, which is what enforces one profile per
   * identity now that it is no longer the primary key.
   */
  @Column(name = "principal_id", nullable = false, unique = true)
  private UUID principalId;

  @Column(name = "first_name", nullable = false, length = 128)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 128)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private UserStatusEnum status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserEntity() {
    // JPA
  }

  public UserEntity(UUID principalId, String firstName, String lastName, Instant now) {
    this.id = UUID.randomUUID();
    this.principalId = principalId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.status = UserStatusEnum.ACTIVE;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPrincipalId() {
    return principalId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public UserStatusEnum getStatus() {
    return status;
  }

  public boolean isActive() {
    return status == UserStatusEnum.ACTIVE;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void changeStatus(UserStatusEnum status, Instant now) {
    this.status = status;
    this.updatedAt = now;
  }

  public User toProto() {
    return User.newBuilder()
        .setId(id.toString())
        .setPrincipalId(principalId.toString())
        .setFirstName(firstName)
        .setLastName(lastName)
        .setStatus(UserStatus.valueOf("USER_STATUS_" + status.name()))
        .setCreatedAt(createdAt.toEpochMilli())
        .setUpdatedAt(updatedAt.toEpochMilli())
        .build();
  }
}
