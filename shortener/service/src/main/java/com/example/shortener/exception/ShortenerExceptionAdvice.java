package com.example.shortener.exception;

import io.grpc.Status;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * The gRPC equivalent of a {@code @RestControllerAdvice}: {@code spring-boot-starter-grpc-server}
 * discovers this bean, resolves each {@code @GrpcExceptionHandler} method by its parameter type,
 * and applies the whole thing as one global interceptor -- no manual registration needed. See
 * {@code GrpcExceptionHandlingTest} for proof of the fallback this replaces: an exception with no
 * handler here surfaces to the client as {@code Status.UNKNOWN}, not a crash.
 */
@GrpcAdvice
public class ShortenerExceptionAdvice {

  @GrpcExceptionHandler
  public Status handleInvalidArgument(InvalidArgumentException e) {
    return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public Status handleUrlFlagged(UrlFlaggedException e) {
    return Status.FAILED_PRECONDITION.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public Status handleAuditUnavailable(AuditUnavailableException e) {
    return Status.UNAVAILABLE.withDescription(e.getMessage());
  }
}
