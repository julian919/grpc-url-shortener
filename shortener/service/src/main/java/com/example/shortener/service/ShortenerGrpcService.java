package com.example.shortener.service;

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
import com.example.shortener.exception.AuditUnavailableException;
import com.example.shortener.exception.InvalidArgumentException;
import com.example.shortener.exception.UrlFlaggedException;
import com.example.shortener.link.LinkStore;
import com.example.shortener.shortcode.ShortCodeGenerator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generated {@code ShortenerServiceImplBase} is an abstract class with one
 * method per rpc
 * in the .proto. Extending it is what makes this class a gRPC service;
 * annotating it
 * {@code @Service} is what makes Spring hand it to the gRPC server at startup.
 */
@Service
public class ShortenerGrpcService extends ShortenerServiceGrpc.ShortenerServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(ShortenerGrpcService.class);

  private final LinkStore store;
  private final ShortCodeGenerator generator;
  private final LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub;

  public ShortenerGrpcService(
      LinkStore store,
      ShortCodeGenerator generator,
      LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub) {
    this.store = store;
    this.generator = generator;
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
          "long_url must be an absolute http or https URL with a host, e.g. https://example.com (got: "
              + request.getLongUrl()
              + ")");
    }

    ShortLink link = ShortLink.newBuilder()
        .setShortCode(generator.next())
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

    store.save(link);

    responseObserver.onNext(CreateShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolveShortLink(
      ResolveShortLinkRequest request, StreamObserver<ResolveShortLinkResponse> responseObserver) {

    ShortLink link = store.findByCode(request.getShortCode())
        .orElseThrow(() -> Status.NOT_FOUND
            .withDescription("no such short code: " + request.getShortCode())
            .asRuntimeException());

    responseObserver.onNext(ResolveShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  /**
   * True only for a URL this service should actually shorten: absolute, http or https, with a
   * non-blank host. {@code URI.create("malware.test")} parses without error but has no scheme
   * at all -- a relative reference. {@code URI.create("javascript:alert(1)")} IS absolute and
   * DOES have a scheme, but not one anything downstream should ever redirect through.
   *
   * <p>Package-private rather than private specifically so the test in this package can
   * exercise it directly -- see lessons/0007b-writing-unit-tests.html.
   */
  static boolean isShortenableUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      // Not even a syntactically valid URI at all (e.g. contains a raw space).
      return false;
    }

    if (!uri.isAbsolute()) {
      return false;
    }

    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      return false;
    }

    return uri.getHost() != null && !uri.getHost().isBlank();
  }

  private boolean validateCreateShortLinkRequest(CreateShortLinkRequest request) {
    if (request.getLongUrl().isBlank()) {
      return false;
    }
    return isShortenableUrl(request.getLongUrl());
  }
}
