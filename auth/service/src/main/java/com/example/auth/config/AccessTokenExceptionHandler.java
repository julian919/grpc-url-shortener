package com.example.auth.config;

import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

/**
 * Gives access-token failures a machine-readable reason. Without this, Spring gRPC's own
 * SecurityGrpcExceptionHandler turns every AuthenticationException into a bare
 * UNAUTHENTICATED "Authentication failed", discarding whether the token was missing,
 * expired, or bad.
 *
 * <p>Boot collects every GrpcExceptionHandler bean into an @Order-sorted list and the first
 * non-null answer wins. SecurityGrpcExceptionHandler has no @Order, so HIGHEST_PRECEDENCE puts
 * this one first. Anything that isn't an AuthenticationException (AccessDeniedException in
 * particular, so PERMISSION_DENIED is unchanged) returns null and falls through.
 *
 * <p>The code stays UNAUTHENTICATED (HTTP 401) for all three, so a client's "on 401, refresh
 * once" logic is unaffected. The reason only adds detail. Refresh-token failures are a
 * separate path: they are domain exceptions mapped in AuthExceptionAdvice, as 400s.
 *
 * <p>Duplicated in shortener-service, matching this project's per-service security config.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessTokenExceptionHandler implements GrpcExceptionHandler {

  /** Same domain as AuthExceptionAdvice, so each (reason, domain) pair is one error everywhere. */
  static final String ERROR_DOMAIN = "auth.example.com";

  @Override
  public StatusException handleException(Throwable exception) {
    if (!(exception instanceof AuthenticationException)) {
      return null;
    }
    if (isExpiredJwt(exception)) {
      return unauthenticated("ACCESS_TOKEN_EXPIRED", "Access token expired");
    }
    // Spring gRPC's AuthenticationProcessInterceptor throws exactly this, with no cause, when
    // no token was presented and the RPC requires one. A presented-but-bad token instead
    // arrives as an OAuth2AuthenticationException wrapping the JWT failure.
    if (exception instanceof BadCredentialsException && exception.getCause() == null) {
      return unauthenticated("ACCESS_TOKEN_MISSING", "Access token is required");
    }
    // Deliberately vague: don't tell the caller why a forged or corrupted token failed.
    return unauthenticated("ACCESS_TOKEN_INVALID", "Authentication failed");
  }

  /**
   * NimbusJwtDecoder reports validation failures as a JwtValidationException carrying a list
   * of OAuth2Errors. Expiry shares its error code (invalid_token) with other failures, so its
   * description, written by Spring's JwtTimestampValidator, is the only thing that tells it
   * apart.
   */
  private static boolean isExpiredJwt(Throwable exception) {
    for (Throwable t = exception; t != null; t = t.getCause()) {
      if (t instanceof JwtValidationException validation) {
        return validation.getErrors().stream()
            .anyMatch(error -> error.getDescription() != null
                && error.getDescription().startsWith("Jwt expired at"));
      }
    }
    return false;
  }

  private static StatusException unauthenticated(String reason, String message) {
    return StatusProto.toStatusException(
        com.google.rpc.Status.newBuilder()
            .setCode(Code.UNAUTHENTICATED_VALUE)
            .setMessage(message)
            .addDetails(Any.pack(ErrorInfo.newBuilder()
                .setReason(reason)
                .setDomain(ERROR_DOMAIN)
                .build()))
            .build());
  }
}
