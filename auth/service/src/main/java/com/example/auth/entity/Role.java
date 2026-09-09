package com.example.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A named bundle of permissions -- a catalog row, not linked to Principal via a foreign key
 * (Principal.roles is just names, string-matched against this table at token-mint time). */
@Entity
@Table(name = "roles")
public class Role {

  @Id
  @Column(name = "name")
  private String name;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "permissions", columnDefinition = "text[]")
  private Set<String> permissions = new HashSet<>();

  protected Role() {
    // JPA
  }

  public Role(String name, Set<String> permissions) {
    this.name = name;
    this.permissions = new HashSet<>(permissions);
  }

  public String getName() {
    return name;
  }

  public Set<String> getPermissions() {
    return permissions;
  }
}
