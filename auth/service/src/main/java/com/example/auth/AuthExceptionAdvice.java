package com.example.auth;

import com.example.auth.principal.exception.InvalidCredentialsException;
import com.example.auth.principal.exception.LoginAlreadyRegisteredException;
import com.example.auth.token.exception.RefreshTokenExpiredException;
import com.example.auth.token.exception.RefreshTokenInvalidException;
import com.example.auth.token.exception.RefreshTokenMissingException;
import com.example.auth.token.exception.RefreshTokenRevokedException;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * Same mechanism as shortener-service's {@code ShortenerExceptionAdvice} -- see
 * lessons/0007c-exceptions-and-grpc-status-codes.html for how this was found and verified.
 *
 * <p>Class and methods MUST be {@code public}: Spring's reflective resolver silently falls
 * back to {@code Status.INTERNAL} for a package-private handler -- a real bug this project hit
 * once already, see the same lesson's Section 6.
 */
@GrpcAdvice
public class AuthExceptionAdvice {

  /** The ErrorInfo domain: with the reason, it identifies an error across services (AIP-193). */
  static final String ERROR_DOMAIN = "auth.example.com";

  @GrpcExceptionHandler
  public Status handleInvalidCredentials(InvalidCredentialsException e) {
    return Status.UNAUTHENTICATED.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public Status handleAlreadyRegistered(LoginAlreadyRegisteredException e) {
    return Status.ALREADY_EXISTS.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public StatusRuntimeException handleRefreshTokenMissing(RefreshTokenMissingException e) {
    return refreshFailure("REFRESH_TOKEN_MISSING", e);
  }

  @GrpcExceptionHandler
  public StatusRuntimeException handleRefreshTokenInvalid(RefreshTokenInvalidException e) {
    return refreshFailure("REFRESH_TOKEN_INVALID", e);
  }

  @GrpcExceptionHandler
  public StatusRuntimeException handleRefreshTokenRevoked(RefreshTokenRevokedException e) {
    return refreshFailure("REFRESH_TOKEN_REVOKED", e);
  }

  @GrpcExceptionHandler
  public StatusRuntimeException handleRefreshTokenExpired(RefreshTokenExpiredException e) {
    return refreshFailure("REFRESH_TOKEN_EXPIRED", e);
  }

  /**
   * A failed refresh is OAuth's {@code invalid_grant}, which RFC 6749 section 5.2 returns as
   * HTTP 400, so INVALID_ARGUMENT (the gRPC code Envoy maps to 400), deliberately NOT
   * UNAUTHENTICATED. That keeps it distinct from an expired access token: a client that
   * retries every 401 by calling RenewToken can never mistake a dead refresh token for another
   * reason to refresh.
   *
   * <p>The reason travels as a google.rpc.ErrorInfo detail in the grpc-status-details-bin
   * trailer. Spring gRPC's advice handler keeps a returned StatusRuntimeException's trailers,
   * and Envoy's convert_grpc_status turns them into the JSON body's {@code details} array, so
   * clients branch on {@code reason}, never on message text.
   */
  private static StatusRuntimeException refreshFailure(String reason, RuntimeException e) {
    return StatusProto.toStatusRuntimeException(
        com.google.rpc.Status.newBuilder()
            .setCode(Code.INVALID_ARGUMENT_VALUE)
            .setMessage(e.getMessage())
            .addDetails(Any.pack(ErrorInfo.newBuilder()
                .setReason(reason)
                .setDomain(ERROR_DOMAIN)
                .build()))
            .build());
  }
}
