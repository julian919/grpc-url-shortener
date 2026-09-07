package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ResolveShortLinkRequest;
import com.example.shortener.api.ResolveShortLinkResponse;
import com.example.shortener.api.ShortenerError;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Levels 2 and 3, in one class.
 *
 * <p>
 * Level 2 (below): isAbsoluteUrl is a pure function, tested the same way as
 * Level 1 --
 * no mocks needed, just many inputs run through one {@code @ParameterizedTest}
 * instead of
 * one method per case.
 *
 * <p>
 * Level 3 (further below): createShortLink and resolveShortLink are NOT pure --
 * they
 * call another service (link-audit, over the network) and report their result
 * through a
 * StreamObserver instead of a return value. A unit test still isolates THIS
 * class alone:
 * the audit stub is mocked so no real network call happens, and the
 * StreamObserver is
 * mocked so the response can be captured and inspected instead of actually
 * being sent
 * anywhere.
 */
@ExtendWith(MockitoExtension.class)
class ShortenerGrpcServiceTest {

  @Mock
  private LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub;
  @Mock
  private StreamObserver<CreateShortLinkResponse> createObserver;
  @Mock
  private StreamObserver<ResolveShortLinkResponse> resolveObserver;

  private ShortenerGrpcService service;

  @BeforeEach
  void setUp() {
    // The real ShortCodeGenerator, not a mock -- it's a pure, trivial dependency
    // (Level 1
    // already tested it directly), so there's nothing to gain from mocking it here.
    // Mock
    // only the dependency that does real I/O: the network call to link-audit.
    service = new ShortenerGrpcService(new ShortCodeGenerator(), auditStub);
  }

  // --- Level 2: pure function, many cases, no mocks
  // ---------------------------------

  @ParameterizedTest
  @ValueSource(strings = { "https://example.com", "http://example.com", "ftp://example.com" })
  void isAbsoluteUrl_trueForAnyScheme(String url) {
    assertThat(ShortenerGrpcService.isAbsoluteUrl(url)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "malware.test", // no scheme -- the exact bug this method exists to catch
      "''", // blank
      "not a url at all" // not even syntactically valid (raw space)
  })
  void isAbsoluteUrl_falseWithoutAScheme(String url) {
    assertThat(ShortenerGrpcService.isAbsoluteUrl(url)).isFalse();
  }

  // --- Level 3: mock the network dependency, capture the response
  // --------------------

  @Test
  void createShortLink_blankUrl_respondsWithInvalidArgumentError() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("").build();

    service.createShortLink(request, createObserver);

    CreateShortLinkResponse response = captureResponse();
    assertThat(response.getResponseCase()).isEqualTo(CreateShortLinkResponse.ResponseCase.ERROR);
    assertThat(response.getError().getCode())
        .isEqualTo(ShortenerError.SHORTENER_ERROR_INVALID_ARGUMENT_VALUE);
    // The audit dependency should never even be called for input this obviously
    // invalid --
    // this is a genuinely useful thing a unit test can check that reading the code
    // cannot
    // confirm at a glance: verify(auditStub, never())... would go here, and is
    // worth adding
    // if you want to lock in "invalid requests short-circuit before any network
    // call".
  }

  @Test
  void createShortLink_auditFlagsTheUrl_respondsWithFlaggedError() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("https://malware.test").build();

    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_FLAGGED)
                .setReason("host is on the blocklist: malware.test")
                .build());

    service.createShortLink(request, createObserver);

    CreateShortLinkResponse response = captureResponse();
    assertThat(response.getResponseCase()).isEqualTo(CreateShortLinkResponse.ResponseCase.ERROR);
    assertThat(response.getError().getCode())
        .isEqualTo(ShortenerError.SHORTENER_ERROR_URL_FLAGGED_VALUE);
  }

  @Test
  void createShortLink_auditUnreachable_respondsWithAuditUnavailableError() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("https://anthropic.com").build();

    when(auditStub.reportLinkCreated(any()))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());

    service.createShortLink(request, createObserver);

    CreateShortLinkResponse response = captureResponse();
    assertThat(response.getResponseCase()).isEqualTo(CreateShortLinkResponse.ResponseCase.ERROR);
    assertThat(response.getError().getCode())
        .isEqualTo(ShortenerError.SHORTENER_ERROR_AUDIT_UNAVAILABLE_VALUE);
  }

  @Test
  void createShortLink_cleanUrl_storesAndReturnsTheLink() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("https://anthropic.com").build();

    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_ACTIVE)
                .build());

    service.createShortLink(request, createObserver);

    CreateShortLinkResponse response = captureResponse();
    assertThat(response.getResponseCase()).isEqualTo(CreateShortLinkResponse.ResponseCase.LINK);
    assertThat(response.getLink().getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(response.getLink().getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);

    // Confirms auditStub was actually called, with the link this createShortLink
    // built --
    // not just that SOME response came back. Mocking without verifying the
    // interaction is
    // an easy way to write a test that would pass even if the audit call were
    // deleted.
    verify(auditStub).reportLinkCreated(any(ReportLinkCreatedRequest.class));
  }

  @Test
  void resolveShortLink_afterCreate_returnsTheSameLink() {
    // No mocking of ShortenerGrpcService's own state here -- store is real,
    // in-memory,
    // exactly as it runs in production. Only the network dependency (audit) is
    // mocked.
    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_ACTIVE)
                .build());
    service.createShortLink(
        CreateShortLinkRequest.newBuilder().setLongUrl("https://anthropic.com").build(),
        createObserver);
    String code = captureResponse().getLink().getShortCode();

    service.resolveShortLink(
        ResolveShortLinkRequest.newBuilder().setShortCode(code).build(), resolveObserver);

    ArgumentCaptor<ResolveShortLinkResponse> captor = ArgumentCaptor.forClass(ResolveShortLinkResponse.class);
    verify(resolveObserver).onNext(captor.capture());
    assertThat(captor.getValue().getLink().getLongUrl()).isEqualTo("https://anthropic.com");
  }

  @Test
  void resolveShortLink_unknownCode_reportsNotFoundStatus() {
    service.resolveShortLink(
        ResolveShortLinkRequest.newBuilder().setShortCode("nope").build(), resolveObserver);

    ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
    verify(resolveObserver).onError(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
    assertThat(Status.fromThrowable(captor.getValue()).getCode())
        .isEqualTo(Status.Code.NOT_FOUND);
  }

  private CreateShortLinkResponse captureResponse() {
    ArgumentCaptor<CreateShortLinkResponse> captor = ArgumentCaptor.forClass(CreateShortLinkResponse.class);
    verify(createObserver).onNext(captor.capture());
    return captor.getValue();
  }
}
