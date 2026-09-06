package com.example.linkaudit;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.LinkStatus;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read the imports. This service imports {@code com.example.shortener.api.LinkStatus} --
 * generated from shortener.proto, owned by another service, compiled from the same jar.
 *
 * <p>That single import is the thing the project is demonstrating. There is no local copy of
 * LinkStatus to drift out of sync, and adding a value to the enum is one edit in one file
 * that both services pick up on the next build.
 */
@Service
public class LinkAuditGrpcService extends LinkAuditServiceGrpc.LinkAuditServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(LinkAuditGrpcService.class);

  private static final Set<String> BLOCKED_HOSTS =
      Set.of("malware.test", "phishing.test", "spam.test");

  @Override
  public void reportLinkCreated(
      ReportLinkCreatedRequest request, StreamObserver<ReportLinkCreatedResponse> responseObserver) {

    String longUrl = request.getLink().getLongUrl();
    String host = hostOf(longUrl);

    ReportLinkCreatedResponse.Builder response = ReportLinkCreatedResponse.newBuilder();

    if (host != null && BLOCKED_HOSTS.contains(host)) {
      log.info("flagging {} (host {})", request.getLink().getShortCode(), host);
      response
          .setResultingStatus(LinkStatus.LINK_STATUS_FLAGGED)
          .setReason("host is on the blocklist: " + host);
    } else {
      response.setResultingStatus(LinkStatus.LINK_STATUS_ACTIVE);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  private static String hostOf(String url) {
    try {
      return URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
