package com.example.shortener.link;

import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.PageInfo;
import com.example.shortener.api.ListShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.link.exception.InvalidArgumentException;
import com.example.shortener.link.entity.ShortLinkEntity;
import com.example.shortener.link.exception.UrlFlaggedException;
import com.example.shortener.link.repository.ShortLinkRepository;
import io.grpc.Status;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * Hosts refused at creation time. This was linkaudit-service, a separate gRPC service called
   * over the wire on every create; it is now a local check, because a hardcoded blocklist lookup
   * never justified its own deployable, its own channel, and a network hop that could fail
   * independently of the thing it was guarding.
   */
  private static final Set<String> BLOCKED_HOSTS =
      Set.of("malware.test", "phishing.test", "spam.test");

  private final ShortLinkRepository repository;
  private final ShortCodeGenerator generator;
  private final AuthorDirectory authorDirectory;

  public LinkService(
      ShortLinkRepository repository, ShortCodeGenerator generator, AuthorDirectory authorDirectory) {
    this.repository = repository;
    this.generator = generator;
    this.authorDirectory = authorDirectory;
  }

  /**
   * @param authorId the caller's principal, taken from the verified JWT's {@code sub} by the gRPC
   *     adapter -- never from the request body, which a client controls
   */
  public ShortLink createShortLink(String longUrl, String authorId) {
    if (!isShortenableUrl(longUrl)) {
      throw new InvalidArgumentException(
          "long_url must be an absolute http or https URL with a host, e.g. https://example.com (got: "
              + longUrl
              + ")");
    }

    // Checked BEFORE a short code is generated: a suspended author should cost us nothing. The
    // token alone cannot answer this -- it stays valid for its full lifetime after a suspension --
    // so this is a live lookup against user-service.
    authorDirectory.requireActiveAuthor(authorId);

    long now = System.currentTimeMillis();
    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode(generator.next())
            .setLongUrl(longUrl)
            .setCreatedAt(now)
            .setUpdatedAt(now)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .setAuthorId(authorId)
            .build();

    auditOrThrow(link);

    repository.save(ShortLinkEntity.fromProto(link));
    return link;
  }

  /**
   * Rejects a link whose host is on the blocklist. Throws rather than returning a status, so a
   * flagged link is never persisted -- the same outcome the remote audit produced, minus the
   * UNAVAILABLE failure mode that only existed because the check lived across a network boundary.
   */
  private void auditOrThrow(ShortLink link) {
    String host = hostOf(link.getLongUrl());
    if (host != null && BLOCKED_HOSTS.contains(host)) {
      String reason = "host is on the blocklist: " + host;
      log.warn("link {} flagged by audit: {}", link.getShortCode(), reason);
      throw new UrlFlaggedException("Shortener URL has been flagged: " + reason);
    }
  }

  private static String hostOf(String url) {
    try {
      return URI.create(url).getHost();
    } catch (IllegalArgumentException notAUrl) {
      return null;
    }
  }

  public ShortLink getShortLink(String shortCode) {
    return repository
        .findById(shortCode)
        .map(ShortLinkEntity::toProto)
        .orElseThrow(
            () ->
                Status.NOT_FOUND
                    .withDescription("no such short code: " + shortCode)
                    .asRuntimeException());
  }

  public ListShortLinksResponse listShortLinks(int requestedPage, int requestedPageSize) {
    if (requestedPage < 0) {
      throw new InvalidArgumentException("page must be non-negative (got: " + requestedPage + ")");
    }
    if (requestedPageSize < 0) {
      throw new InvalidArgumentException(
          "page_size must be non-negative (got: " + requestedPageSize + ")");
    }

    int page = requestedPage == 0 ? 1 : requestedPage;
    int pageSize =
        requestedPageSize == 0 ? DEFAULT_PAGE_SIZE : Math.min(requestedPageSize, MAX_PAGE_SIZE);

    int offset = (page - 1) * pageSize;
    int totalCount = (int) repository.count();
    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);

    List<ShortLink> links =
        repository.listPaged(offset, pageSize).stream().map(ShortLinkEntity::toProto).toList();

    PageInfo pageInfo =
        PageInfo.newBuilder()
            .setPage(page)
            .setPageSize(pageSize)
            .setTotalCount(totalCount)
            .setTotalPages(totalPages)
            .build();

    return ListShortLinksResponse.newBuilder()
        .addAllShortLinks(links)
        .setPageInfo(pageInfo)
        .build();
  }

  /**
   * Partial update. {@code longUrl} and {@code status} are NULL when the caller did not send them
   * -- the gRPC adapter maps proto3 explicit presence (hasLongUrl/hasStatus) onto null here, so
   * "absent" and "the zero value" stay distinguishable without a FieldMask.
   *
   * <p>Two rules this enforces that a naive setter-copy would miss:
   *
   * <ul>
   *   <li>changing longUrl RE-RUNS the audit. Otherwise update is a trivial bypass of the
   *       blocklist: create with a clean URL, then patch it to a blocked one.
   *   <li>a client may not set FLAGGED. That value is the audit's verdict, not a caller's opinion,
   *       and letting it be written by hand would make the field meaningless.
   * </ul>
   *
   * <p>Transactional because the entity is mutated in place: Hibernate dirty-checks the managed
   * instance and writes at commit, so there is no explicit save call here.
   */
  @Transactional
  public ShortLink updateShortLink(String shortCode, String longUrl, LinkStatus status) {
    if (shortCode == null || shortCode.isBlank()) {
      throw new InvalidArgumentException("short_code is required");
    }
    if (longUrl == null && status == null) {
      throw new InvalidArgumentException("nothing to update: send long_url and/or status");
    }

    ShortLinkEntity entity =
        repository
            .findById(shortCode)
            .orElseThrow(
                () ->
                    Status.NOT_FOUND
                        .withDescription("no such short code: " + shortCode)
                        .asRuntimeException());

    if (longUrl != null) {
      if (!isShortenableUrl(longUrl)) {
        throw new InvalidArgumentException(
            "long_url must be an absolute http or https URL with a host (got: " + longUrl + ")");
      }
      // Re-audit: the blocklist applies to the new destination exactly as it did at creation.
      auditOrThrow(ShortLink.newBuilder().setShortCode(shortCode).setLongUrl(longUrl).build());
    }

    if (status != null) {
      if (status == LinkStatus.LINK_STATUS_UNSPECIFIED) {
        throw new InvalidArgumentException(
            "status must be a real value, not LINK_STATUS_UNSPECIFIED");
      }
      if (status == LinkStatus.LINK_STATUS_FLAGGED) {
        throw new InvalidArgumentException(
            "status LINK_STATUS_FLAGGED is set by the audit, not by clients");
      }
    }

    entity.applyUpdate(longUrl, status, Instant.now());
    log.info(
        "updated link {} (long_url: {}, status: {})", shortCode, longUrl != null, status != null);
    return entity.toProto();
  }

  public static boolean isShortenableUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return false;
    }
    URI uri;
    try {
      uri = URI.create(raw);
    } catch (IllegalArgumentException notAUrl) {
      return false;
    }
    if (!uri.isAbsolute() || uri.getHost() == null) {
      return false;
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    return "http".equals(scheme) || "https".equals(scheme);
  }
}
