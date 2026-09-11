package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.token.exception.RefreshTokenExpiredException;
import com.example.auth.token.exception.RefreshTokenInvalidException;
import com.example.auth.token.exception.RefreshTokenMissingException;
import com.example.auth.token.exception.RefreshTokenRevokedException;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Reads the error back the way a client does, via StatusProto.fromThrowable, which decodes
 * the grpc-status-details-bin trailer, so this checks the trailer is actually populated, not
 * just that a Status was returned.
 */
class AuthExceptionAdviceTest {

  private static final AuthExceptionAdvice advice = new AuthExceptionAdvice();

  static Stream<Arguments> refreshFailures() {
    return Stream.of(
        Arguments.of(
            (Supplier<StatusRuntimeException>)
                () -> advice.handleRefreshTokenMissing(new RefreshTokenMissingException()),
            "REFRESH_TOKEN_MISSING"),
        Arguments.of(
            (Supplier<StatusRuntimeException>)
                () -> advice.handleRefreshTokenInvalid(new RefreshTokenInvalidException()),
            "REFRESH_TOKEN_INVALID"),
        Arguments.of(
            (Supplier<StatusRuntimeException>)
                () -> advice.handleRefreshTokenRevoked(new RefreshTokenRevokedException()),
            "REFRESH_TOKEN_REVOKED"),
        Arguments.of(
            (Supplier<StatusRuntimeException>)
                () -> advice.handleRefreshTokenExpired(new RefreshTokenExpiredException()),
            "REFRESH_TOKEN_EXPIRED"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("refreshFailures")
  void refreshFailure_isInvalidArgumentCarryingAMachineReadableReason(
      Supplier<StatusRuntimeException> handler, String expectedReason) throws Exception {
    StatusRuntimeException error = handler.get();

    // invalid_grant -> 400, not 401: must never look like an expired access token.
    assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);

    com.google.rpc.Status details = StatusProto.fromThrowable(error);
    assertThat(details).isNotNull();
    assertThat(details.getDetailsCount()).isEqualTo(1);

    ErrorInfo info = details.getDetails(0).unpack(ErrorInfo.class);
    assertThat(info.getReason()).isEqualTo(expectedReason);
    assertThat(info.getDomain()).isEqualTo(AuthExceptionAdvice.ERROR_DOMAIN);
    // AIP-193's required shape for a reason.
    assertThat(info.getReason()).matches("[A-Z][A-Z0-9_]+[A-Z0-9]").hasSizeLessThanOrEqualTo(63);
  }
}
