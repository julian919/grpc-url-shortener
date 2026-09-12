package com.example.shortener;

import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.GetShortLinkRequest;
import com.example.shortener.api.GetShortLinkResponse;
import com.example.shortener.api.ListShortLinksRequest;
import com.example.shortener.api.ListShortLinksResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ShortLink;
import com.example.shortener.api.UpdateShortLinkRequest;
import com.example.shortener.api.UpdateShortLinkResponse;
import com.example.shortener.api.ShortenerServiceGrpc;
import com.example.shortener.link.LinkService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * The generated {@code ShortenerServiceImplBase} is an abstract class with one
 * method per rpc in the .proto. Extending it is what makes this class a gRPC service;
 * annotating it {@code @Service} is what makes Spring hand it to the gRPC server at startup.
 *
 * Acting strictly as a gRPC transport adapter, this class delegates business operations
 * to {@link LinkService}.
 */
@Service
public class ShortenerGrpcService extends ShortenerServiceGrpc.ShortenerServiceImplBase {

  private final LinkService linkService;

  public ShortenerGrpcService(LinkService linkService) {
    this.linkService = linkService;
  }

  /**
   * The caller's identity, straight off the verified JWT. Spring Security's resource-server filter
   * has already populated the context by the time any rpc method runs, so this is the gRPC
   * equivalent of Cognixus reading {@code ctx.Value("principalId")} -- no interceptor of our own
   * needed, because the framework put it there.
   */
  private static String callerPrincipalId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw Status.UNAUTHENTICATED.withDescription("no authenticated caller").asRuntimeException();
    }
    return jwt.getSubject();
  }

  @Override
  public void createShortLink(
      CreateShortLinkRequest request, StreamObserver<CreateShortLinkResponse> responseObserver) {
    ShortLink link = linkService.createShortLink(request.getLongUrl(), callerPrincipalId());
    responseObserver.onNext(CreateShortLinkResponse.newBuilder().setShortLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getShortLink(
      GetShortLinkRequest request, StreamObserver<GetShortLinkResponse> responseObserver) {
    ShortLink link = linkService.getShortLink(request.getShortCode());
    responseObserver.onNext(GetShortLinkResponse.newBuilder().setShortLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void updateShortLink(
      UpdateShortLinkRequest request, StreamObserver<UpdateShortLinkResponse> responseObserver) {
    // Explicit presence -> null: hasLongUrl() is false when the client omitted the field, which
    // is what keeps "absent" distinguishable from "the zero value" without a FieldMask.
    ShortLink link =
        linkService.updateShortLink(
            request.getShortCode(),
            request.hasLongUrl() ? request.getLongUrl() : null,
            request.hasStatus() ? request.getStatus() : null);
    responseObserver.onNext(UpdateShortLinkResponse.newBuilder().setShortLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void listShortLinks(
      ListShortLinksRequest request,
      StreamObserver<ListShortLinksResponse> responseObserver) {
    ListShortLinksResponse response =
        linkService.listShortLinks(request.getPage(), request.getPageSize());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
