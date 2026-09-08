package com.example.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ResolveShortLinkRequest;
import com.example.shortener.api.ResolveShortLinkResponse;
import com.example.shortener.exception.AuditUnavailableException;
import com.example.shortener.exception.InvalidArgumentException;
import com.example.shortener.exception.UrlFlaggedException;
import com.example.shortener.link.InMemoryLinkStore;
import com.example.shortener.shortcode.ShortCodeGenerator;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Levels 2 and 3, in one class.
 *
 * <p>Level 2 (below): isShortenableUrl is a pure function, tested the same way as Level 1 -- no
 * mocks needed, just many inputs run through one {@code @ParameterizedTest} instead of one
 * method per case.
 *
 * <p>Level 3 (further below): createShortLink and resolveShortLink are NOT pure -- they call
 * another service (link-audit, over the network) and, on the happy path, report their result
 * through a StreamObserver. On a failure path they instead THROW -- {@code ShortenerExceptionAdvice}
 * is what turns that into a Status the client sees in the real, running app, but a unit test at
 * this layer only needs to prove the right exception type comes out; see
 * {@code GrpcExceptionHandlingTest} for proof of the translation step itself.
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
    // The real InMemoryLinkStore and ShortCodeGenerator, not mocks -- both are simple,
    // already-tested-in-isolation collaborators (Level 1), so there's nothing to gain from
    // mocking them here. Mock only the dependency that does real I/O: the network call to
    // link-audit.
    service = new ShortenerGrpcService(new InMemoryLinkStore(), new ShortCodeGenerator(), auditStub);
  }

  // --- Level 2: pure function, many cases, no mocks ---------------------------------

  @ParameterizedTest
  @ValueSource(strings = { "https://example.com", "http://example.com" })
  void isShortenableUrl_trueForHttpAndHttps(String url) {
    assertThat(ShortenerGrpcService.isShortenableUrl(url)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "malware.test", // no scheme at all -- the original malware.test bug
      "''", // blank
      "not a url at all", // not even syntactically valid (raw space)
      "ftp://example.com", // absolute, has a scheme, but not one we shorten
      "javascript:alert(1)", // absolute, has a scheme, definitely not one we shorten
      "https://", // http(s) scheme, but no host at all
  })
  void isShortenableUrl_falseForAnythingElse(String url) {
    assertThat(ShortenerGrpcService.isShortenableUrl(url)).isFalse();
  }

  // --- Level 3: mock the network dependency, assert on the thrown exception --------

  @Test
  void createShortLink_blankUrl_throwsInvalidArgument() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("").build();

    assertThatThrownBy(() -> service.createShortLink(request, createObserver))
        .isInstanceOf(InvalidArgumentException.class);

    // Invalid requests short-circuit before any network call -- worth locking in
    // explicitly, since reading the code alone doesn't confirm it at a glance.
    verifyNoInteractions(auditStub);
  }

  @Test
  void createShortLink_auditFlagsTheUrl_throwsUrlFlagged() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("https://malware.test").build();

    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_FLAGGED)
                .setReason("host is on the blocklist: malware.test")
                .build());

    assertThatThrownBy(() -> service.createShortLink(request, createObserver))
        .isInstanceOf(UrlFlaggedException.class);
  }

  @Test
  void createShortLink_auditUnreachable_throwsAuditUnavailable() {
    CreateShortLinkRequest request = CreateShortLinkRequest.newBuilder().setLongUrl("https://anthropic.com").build();

    when(auditStub.reportLinkCreated(any()))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());

    // Wrapped, not left as the raw StatusRuntimeException the stub threw -- this
    // service's error vocabulary shouldn't leak link-audit's.
    assertThatThrownBy(() -> service.createShortLink(request, createObserver))
        .isInstanceOf(AuditUnavailableException.class)
        .hasCauseInstanceOf(StatusRuntimeException.class);
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
    assertThat(response.getLink().getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(response.getLink().getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);

    // Confirms auditStub was actually called, with the link this createShortLink
    // built -- not just that SOME response came back. Mocking without verifying
    // the interaction is an easy way to write a test that would pass even if the
    // audit call were deleted.
    verify(auditStub).reportLinkCreated(any(ReportLinkCreatedRequest.class));
  }

  @Test
  void resolveShortLink_afterCreate_returnsTheSameLink() {
    // No mocking of ShortenerGrpcService's own storage here -- InMemoryLinkStore is real,
    // exactly as it runs in production. Only the network dependency (audit) is mocked.
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
  void resolveShortLink_unknownCode_throwsNotFoundStatus() {
    ResolveShortLinkRequest request = ResolveShortLinkRequest.newBuilder().setShortCode("nope").build();

    assertThatThrownBy(() -> service.resolveShortLink(request, resolveObserver))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.NOT_FOUND));

    verifyNoInteractions(resolveObserver);
  }

  private CreateShortLinkResponse captureResponse() {
    ArgumentCaptor<CreateShortLinkResponse> captor = ArgumentCaptor.forClass(CreateShortLinkResponse.class);
    verify(createObserver).onNext(captor.capture());
    return captor.getValue();
  }
}
