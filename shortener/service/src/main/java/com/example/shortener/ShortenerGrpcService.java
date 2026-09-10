package com.example.shortener;

import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.ResolveShortLinkRequest;
import com.example.shortener.api.ResolveShortLinkResponse;
import com.example.shortener.api.RetrieveShortLinksRequest;
import com.example.shortener.api.RetrieveShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.api.ShortenerServiceGrpc;
import com.example.shortener.link.LinkService;
import io.grpc.stub.StreamObserver;
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

  @Override
  public void createShortLink(
      CreateShortLinkRequest request, StreamObserver<CreateShortLinkResponse> responseObserver) {
    ShortLink link = linkService.createShortLink(request.getLongUrl());
    responseObserver.onNext(CreateShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolveShortLink(
      ResolveShortLinkRequest request, StreamObserver<ResolveShortLinkResponse> responseObserver) {
    ShortLink link = linkService.resolveShortLink(request.getShortCode());
    responseObserver.onNext(ResolveShortLinkResponse.newBuilder().setLink(link).build());
    responseObserver.onCompleted();
  }

  @Override
  public void retrieveShortLinks(
      RetrieveShortLinksRequest request,
      StreamObserver<RetrieveShortLinksResponse> responseObserver) {
    RetrieveShortLinksResponse response =
        linkService.retrieveShortLinks(request.getPage(), request.getPageSize());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
