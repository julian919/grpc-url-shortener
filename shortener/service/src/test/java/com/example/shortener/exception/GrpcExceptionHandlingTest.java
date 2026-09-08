package com.example.shortener.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.ShortenerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;

/**
 * Exercises the REAL {@code spring-boot-starter-grpc-server} exception-translation
 * mechanism directly (no Spring context needed -- {@link GrpcExceptionHandlerInterceptor} is
 * a plain grpc-java {@code ServerInterceptor}), rather than trusting a claim about its
 * behavior. This is the thing {@code GrpcServerAutoConfiguration} wires up for every gRPC
 * server in the app; a {@code @GrpcAdvice} bean is just a reflective way of supplying the
 * {@link GrpcExceptionHandler} this test constructs by hand.
 */
class GrpcExceptionHandlingTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  void exceptionWithNoMappingSurfacesAsUnknown() throws IOException {
    // The handler recognizes nothing -- every call falls through to
    // GrpcExceptionHandlerInterceptor's own fallback.
    startServerThrowing(new IllegalStateException("boom"), exception -> null);

    StatusRuntimeException thrown = callAndCaptureError();

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
  }

  @Test
  void exceptionWithAMappingSurfacesAsItsMappedCode() throws IOException {
    startServerThrowing(
        new IllegalArgumentException("bad url"),
        exception -> exception instanceof IllegalArgumentException
            ? Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asException()
            : null);

    StatusRuntimeException thrown = callAndCaptureError();

    assertThat(thrown.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(thrown.getStatus().getDescription()).isEqualTo("bad url");
  }

  private void startServerThrowing(RuntimeException toThrow, GrpcExceptionHandler handler) throws IOException {
    String name = "exception-handling-test-" + UUID.randomUUID();

    ShortenerServiceGrpc.ShortenerServiceImplBase throwingService =
        new ShortenerServiceGrpc.ShortenerServiceImplBase() {
          @Override
          public void createShortLink(
              CreateShortLinkRequest request, StreamObserver<CreateShortLinkResponse> responseObserver) {
            throw toThrow;
          }
        };

    server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(throwingService)
        .intercept(new GrpcExceptionHandlerInterceptor(handler))
        .build()
        .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
  }

  private StatusRuntimeException callAndCaptureError() {
    ShortenerServiceGrpc.ShortenerServiceBlockingStub stub = ShortenerServiceGrpc.newBlockingStub(channel);
    return catchThrowableOfType(
        StatusRuntimeException.class,
        () -> stub.createShortLink(
            CreateShortLinkRequest.newBuilder().setLongUrl("https://example.com").build()));
  }
}
