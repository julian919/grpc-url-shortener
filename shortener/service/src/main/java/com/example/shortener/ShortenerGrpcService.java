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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generated {@code ShortenerServiceImplBase} is an abstract class with one method per rpc
 * in the .proto. Extending it is what makes this class a gRPC service; annotating it
 * {@code @Service} is what makes Spring hand it to the gRPC server at startup.
 *
 * <p>Storage is an in-memory map for now. Postgres + Redis cache-aside arrive in a later
 * lesson; nothing above this line changes when they do, which is itself the point.
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

    if (request.getLongUrl().isBlank()) {
      // gRPC's error model is a status code, not an HTTP code and not an exception type.
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("long_url must not be empty").asRuntimeException());
      return;
    }

    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode(codes.next())
            .setLongUrl(request.getLongUrl())
            .setCreatedAt(System.currentTimeMillis())
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    // Cross-service call. Note that the verdict comes back as a LinkStatus -- the SAME
    // generated enum both services compiled against. Neither side owns a private copy.
    LinkStatus verdict = link.getStatus();
    try {
      ReportLinkCreatedResponse audit =
          auditStub.reportLinkCreated(
              ReportLinkCreatedRequest.newBuilder().setLink(link).build());
      verdict = audit.getResultingStatus();
      if (verdict == LinkStatus.LINK_STATUS_FLAGGED) {
        log.warn("link {} flagged by audit: {}", link.getShortCode(), audit.getReason());
      }
    } catch (RuntimeException e) {
      // Audit is advisory. A shortener that cannot create links because the auditor is
      // down has coupled its availability to a non-critical dependency.
      log.warn("audit unavailable, defaulting to {}: {}", verdict, e.toString());
    }

    ShortLink stored = link.toBuilder().setStatus(verdict).build();
    store.put(stored.getShortCode(), stored);

    responseObserver.onNext(CreateShortLinkResponse.newBuilder().setLink(stored).build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolveShortLink(
      ResolveShortLinkRequest request, StreamObserver<ResolveShortLinkResponse> responseObserver) {

    ShortLink link = store.get(request.getShortCode());

    if (link == null) {
      responseObserver.onError(
          Status.NOT_FOUND
              .withDescription("no such short code: " + request.getShortCode())
              .asRuntimeException());
      return;
    }

    responseObserver.onNext(ResolveShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }
}
