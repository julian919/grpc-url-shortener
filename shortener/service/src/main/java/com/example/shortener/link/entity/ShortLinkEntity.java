package com.example.shortener.link.entity;

import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ShortLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "short_links")
public class ShortLinkEntity {

  @Id
  @Column(name = "short_code", nullable = false, length = 64)
  private String shortCode;

  @Column(name = "long_url", nullable = false, columnDefinition = "text")
  private String longUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Nullable: links created before author tracking existed have none. */
  @Column(name = "author_id")
  private UUID authorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private LinkStatus status;

  protected ShortLinkEntity() {
    // JPA
  }

  public ShortLinkEntity(
      String shortCode, String longUrl, Instant createdAt, Instant updatedAt, LinkStatus status,
      UUID authorId) {
    this.authorId = authorId;
    this.shortCode = shortCode;
    this.longUrl = longUrl;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.status = status;
  }

  public static ShortLinkEntity fromProto(ShortLink proto) {
    Instant created =
        proto.getCreatedAt() > 0 ? Instant.ofEpochMilli(proto.getCreatedAt()) : Instant.now();
    Instant updated =
        proto.getUpdatedAt() > 0 ? Instant.ofEpochMilli(proto.getUpdatedAt()) : created;
    UUID author = proto.getAuthorId().isEmpty() ? null : UUID.fromString(proto.getAuthorId());
    return new ShortLinkEntity(
        proto.getShortCode(), proto.getLongUrl(), created, updated, proto.getStatus(), author);
  }

  public ShortLink toProto() {
    return ShortLink.newBuilder()
        .setShortCode(shortCode)
        .setLongUrl(longUrl)
        .setCreatedAt(createdAt.toEpochMilli())
        .setUpdatedAt(updatedAt.toEpochMilli())
        .setStatus(status)
        .setAuthorId(authorId == null ? "" : authorId.toString())
        .build();
  }

  public String getShortCode() {
    return shortCode;
  }

  public String getLongUrl() {
    return longUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public LinkStatus getStatus() {
    return status;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  /**
   * Applies an update in place. Called inside a transaction on a managed entity, so Hibernate's
   * dirty checking writes the change at commit -- no explicit save needed. {@code updatedAt} is
   * server-owned: it is stamped here and never taken from the caller.
   */
  public void applyUpdate(String newLongUrl, LinkStatus newStatus, Instant now) {
    if (newLongUrl != null) {
      this.longUrl = newLongUrl;
    }
    if (newStatus != null) {
      this.status = newStatus;
    }
    this.updatedAt = now;
  }
}
