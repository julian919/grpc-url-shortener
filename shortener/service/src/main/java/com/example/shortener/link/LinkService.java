package com.example.shortener.link;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.PageInfo;
import com.example.shortener.api.RetrieveShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.exception.InvalidArgumentException;
import com.example.shortener.link.entity.ShortLinkEntity;
import com.example.shortener.link.exception.AuditUnavailableException;
import com.example.shortener.link.exception.UrlFlaggedException;
import com.example.shortener.link.repository.ShortLinkRepository;
import io.grpc.Status;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;

  private final ShortLinkRepository repository;
  private final ShortCodeGenerator generator;
  private final LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub;

  public LinkService(
      ShortLinkRepository repository,
      ShortCodeGenerator generator,
      LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub) {
    this.repository = repository;
    this.generator = generator;
    this.auditStub = auditStub;
  }

  public ShortLink createShortLink(String longUrl) {
    if (!isShortenableUrl(longUrl)) {
      throw new InvalidArgumentException(
          "long_url must be an absolute http or https URL with a host, e.g. https://example.com (got: "
              + longUrl
              + ")");
    }

    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode(generator.next())
            .setLongUrl(longUrl)
            .setCreatedAt(System.currentTimeMillis())
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    ReportLinkCreatedResponse audit;
    try {
      audit =
          auditStub.reportLinkCreated(
              ReportLinkCreatedRequest.newBuilder().setLink(link).build());
    } catch (RuntimeException e) {
      throw new AuditUnavailableException("failed to verify url " + link.getLongUrl(), e);
    }

    if (audit.getResultingStatus() == LinkStatus.LINK_STATUS_FLAGGED) {
      log.warn("link {} flagged by audit: {}", link.getShortCode(), audit.getReason());
      throw new UrlFlaggedException("Shortener URL has been flagged: " + audit.getReason());
    }

    repository.save(ShortLinkEntity.fromProto(link));
    return link;
  }

  public ShortLink resolveShortLink(String shortCode) {
    return repository
        .findById(shortCode)
        .map(ShortLinkEntity::toProto)
        .orElseThrow(
            () ->
                Status.NOT_FOUND
                    .withDescription("no such short code: " + shortCode)
                    .asRuntimeException());
  }

  public RetrieveShortLinksResponse retrieveShortLinks(int requestedPage, int requestedPageSize) {
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

    return RetrieveShortLinksResponse.newBuilder()
        .addAllLinks(links)
        .setPageInfo(pageInfo)
        .build();
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
