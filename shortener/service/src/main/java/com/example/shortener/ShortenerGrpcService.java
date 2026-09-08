package com.example.shortener;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ResolveShortLinkRequest;
import com.example.shortener.api.ResolveShortLinkResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.api.ShortenerServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generated {@code ShortenerServiceImplBase} is an abstract class with one
 * method per rpc
 * in the .proto. Extending it is what makes this class a gRPC service;
 * annotating it
 * {@code @Service} is what makes Spring hand it to the gRPC server at startup.
 *
 * <p>
 * Storage is an in-memory map for now. Postgres + Redis cache-aside arrive in a
 * later
 * lesson; nothing above this line changes when they do, which is itself the
 * point.
 */
@Service
public class ShortenerGrpcService extends ShortenerServiceGrpc.ShortenerServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(ShortenerGrpcService.class);

  private final Map<String, ShortLink> store = new ConcurrentHashMap<>();
  private final ShortCodeGenerator codes;
  private final LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub;

  public ShortenerGrpcService(
      ShortCodeGenerator codes, LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub) {
    this.codes = codes;
    this.auditStub = auditStub;
  }

  @Override
  public void createShortLink(
      CreateShortLinkRequest request, StreamObserver<CreateShortLinkResponse> responseObserver) {

    if (!validateCreateShortLinkRequest(request)) {
      // Thrown, not returned -- ShortenerExceptionAdvice maps this to
      // Status.INVALID_ARGUMENT globally. Nothing here builds a Status or touches
      // responseObserver on the failure path; that's the advice's job.
      throw new InvalidArgumentException(
          "long_url must be an absolute URL, e.g. https://example.com (got: "
              + request.getLongUrl()
              + ")");
    }

    ShortLink link = ShortLink.newBuilder()
        .setShortCode(codes.next())
        .setLongUrl(request.getLongUrl())
        .setCreatedAt(System.currentTimeMillis())
        .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
        .build();

    // Cross-service call. Note that the verdict comes back as a LinkStatus -- the
    // SAME generated enum both services compiled against. Neither side owns a
    // private copy.
    ReportLinkCreatedResponse audit;
    try {
      audit = auditStub.reportLinkCreated(
          ReportLinkCreatedRequest.newBuilder().setLink(link).build());
    } catch (RuntimeException e) {
      // Fails CLOSED: if audit can't be reached at all, the link is rejected
      // rather than created. Wrapped (rather than left as whatever the stub
      // threw) so this service's error vocabulary doesn't leak link-audit's.
      throw new AuditUnavailableException("failed to verify url " + link.getLongUrl(), e);
    }

    if (audit.getResultingStatus() == LinkStatus.LINK_STATUS_FLAGGED) {
      // Different from AuditUnavailableException above -- audit DID run here,
      // and said no, rather than never answering. Distinct exception, distinct
      // Status code (FAILED_PRECONDITION, not UNAVAILABLE).
      log.warn("link {} flagged by audit: {}", link.getShortCode(), audit.getReason());
      throw new UrlFlaggedException("Shortener URL has been flagged: " + audit.getReason());
    }

    store.put(link.getShortCode(), link);

    responseObserver.onNext(CreateShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolveShortLink(
      ResolveShortLinkRequest request, StreamObserver<ResolveShortLinkResponse> responseObserver) {

    ShortLink link = store.get(request.getShortCode());

    if (link == null) {
      throw Status.NOT_FOUND
          .withDescription("no such short code: " + request.getShortCode())
          .asRuntimeException();
    }

    responseObserver.onNext(ResolveShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  /**
   * True only for a URL with a scheme (https://, http://, ...).
   * {@code URI.create("malware.test")}
   * parses without error but has no scheme and no host -- it's a relative
   * reference, not an
   * absolute URL, and nothing downstream can redirect to it.
   *
   * <p>Package-private rather than private specifically so the test in this package can
   * exercise it directly -- see lessons/0007b-writing-unit-tests.html.
   */
  static boolean isAbsoluteUrl(String url) {
    try {
      return URI.create(url).isAbsolute();
    } catch (IllegalArgumentException e) {
      // Not even a syntactically valid URI at all (e.g. contains a raw space).
      return false;
    }
  }

  private boolean validateCreateShortLinkRequest(CreateShortLinkRequest request) {
    if (request.getLongUrl().isBlank()) {
      return false;
    }
    return isAbsoluteUrl(request.getLongUrl());
  }
}
