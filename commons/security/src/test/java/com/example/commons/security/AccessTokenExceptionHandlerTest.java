package com.example.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Each input mirrors what Spring Security / Spring gRPC actually throw for that case (see the
 * comments in AccessTokenExceptionHandler). The error is read back via
 * StatusProto.fromThrowable, the way a client decodes the trailer.
 */
class AccessTokenExceptionHandlerTest {

  private final AccessTokenExceptionHandler handler = new AccessTokenExceptionHandler();

  @Test
  void expiredJwt_isUnauthenticatedWithExpiredReason() throws Exception {
    OAuth2Error expired = new OAuth2Error(
        OAuth2ErrorCodes.INVALID_TOKEN, "Jwt expired at 2026-09-11T05:01:36Z", null);
    Exception thrown = new InvalidBearerTokenException(
        "An error occurred while attempting to decode the Jwt: Jwt expired at 2026-09-11T05:01:36Z",
        new JwtValidationException("Jwt expired", List.of(expired)));

    assertReason(handler.handleException(thrown), "ACCESS_TOKEN_EXPIRED");
  }

  @Test
  void badSignature_isUnauthenticatedWithInvalidReason() throws Exception {
    Exception thrown = new InvalidBearerTokenException(
        "An error occurred while attempting to decode the Jwt: Signed JWT rejected",
        new BadJwtException("Signed JWT rejected: Invalid signature"));

    assertReason(handler.handleException(thrown), "ACCESS_TOKEN_INVALID");
  }

  @Test
  void noTokenOnAProtectedRpc_isUnauthenticatedWithMissingReason() throws Exception {
    // What AuthenticationProcessInterceptor throws for an anonymous caller.
    assertReason(
        handler.handleException(new BadCredentialsException("not authenticated")),
        "ACCESS_TOKEN_MISSING");
  }

  @Test
  void accessDenied_fallsThroughSoItStaysPermissionDenied() {
    assertThat(handler.handleException(new AccessDeniedException("not allowed"))).isNull();
  }

  @Test
  void nonSecurityException_fallsThrough() {
    assertThat(handler.handleException(new IllegalStateException("boom"))).isNull();
  }

  private static void assertReason(StatusException error, String expectedReason) throws Exception {
    assertThat(error).isNotNull();
    assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);

    com.google.rpc.Status details = StatusProto.fromThrowable(error);
    assertThat(details).isNotNull();
    ErrorInfo info = details.getDetails(0).unpack(ErrorInfo.class);
    assertThat(info.getReason()).isEqualTo(expectedReason);
    assertThat(info.getDomain()).isEqualTo(AccessTokenExceptionHandler.ERROR_DOMAIN);
  }
}
