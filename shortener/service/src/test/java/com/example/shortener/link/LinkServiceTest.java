package com.example.shortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.linkaudit.api.LinkAuditServiceGrpc;
import com.example.linkaudit.api.ReportLinkCreatedRequest;
import com.example.linkaudit.api.ReportLinkCreatedResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.RetrieveShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.exception.InvalidArgumentException;
import com.example.shortener.link.entity.ShortLinkEntity;
import com.example.shortener.link.exception.AuditUnavailableException;
import com.example.shortener.link.exception.UrlFlaggedException;
import com.example.shortener.link.repository.ShortLinkRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  @Mock
  private ShortLinkRepository repository;
  @Mock
  private LinkAuditServiceGrpc.LinkAuditServiceBlockingStub auditStub;

  private LinkService linkService;

  @BeforeEach
  void setUp() {
    linkService = new LinkService(repository, new ShortCodeGenerator(), auditStub);
  }

  // --- URL Validation tests ---------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = { "https://example.com", "http://example.com" })
  void isShortenableUrl_trueForHttpAndHttps(String url) {
    assertThat(LinkService.isShortenableUrl(url)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "malware.test",
      "''",
      "not a url at all",
      "ftp://example.com",
      "javascript:alert(1)",
      "https://",
  })
  void isShortenableUrl_falseForAnythingElse(String url) {
    assertThat(LinkService.isShortenableUrl(url)).isFalse();
  }

  // --- createShortLink tests --------------------------------------------------------

  @Test
  void createShortLink_blankUrl_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.createShortLink(""))
        .isInstanceOf(InvalidArgumentException.class);

    verifyNoInteractions(auditStub);
  }

  @Test
  void createShortLink_auditFlagsTheUrl_throwsUrlFlagged() {
    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_FLAGGED)
                .setReason("host is on the blocklist: malware.test")
                .build());

    assertThatThrownBy(() -> linkService.createShortLink("https://malware.test"))
        .isInstanceOf(UrlFlaggedException.class);
  }

  @Test
  void createShortLink_auditUnreachable_throwsAuditUnavailable() {
    when(auditStub.reportLinkCreated(any()))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());

    assertThatThrownBy(() -> linkService.createShortLink("https://anthropic.com"))
        .isInstanceOf(AuditUnavailableException.class)
        .hasCauseInstanceOf(StatusRuntimeException.class);
  }

  @Test
  void createShortLink_cleanUrl_storesAndReturnsTheLink() {
    when(auditStub.reportLinkCreated(any()))
        .thenReturn(
            ReportLinkCreatedResponse.newBuilder()
                .setResultingStatus(LinkStatus.LINK_STATUS_ACTIVE)
                .build());

    ShortLink link = linkService.createShortLink("https://anthropic.com");

    assertThat(link.getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(link.getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);
    assertThat(link.getShortCode()).isNotEmpty();

    verify(auditStub).reportLinkCreated(any(ReportLinkCreatedRequest.class));
    verify(repository).save(any(ShortLinkEntity.class));
  }

  // --- resolveShortLink tests -------------------------------------------------------

  @Test
  void resolveShortLink_existingCode_returnsTheLink() {
    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    when(repository.findById("abc1234")).thenReturn(Optional.of(ShortLinkEntity.fromProto(link)));

    ShortLink resolved = linkService.resolveShortLink("abc1234");
    assertThat(resolved.getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(resolved.getShortCode()).isEqualTo("abc1234");
  }

  @Test
  void resolveShortLink_unknownCode_throwsNotFoundStatus() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> linkService.resolveShortLink("nope"))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  // --- retrieveShortLinks tests -----------------------------------------------------

  @Test
  void retrieveShortLinks_emptyStore_returnsEmptyListAndZeroPageInfo() {
    when(repository.count()).thenReturn(0L);
    when(repository.listPaged(0, 10)).thenReturn(List.of());

    RetrieveShortLinksResponse response = linkService.retrieveShortLinks(0, 0);

    assertThat(response.getLinksList()).isEmpty();
    assertThat(response.getPageInfo().getPage()).isEqualTo(1);
    assertThat(response.getPageInfo().getPageSize()).isEqualTo(10);
    assertThat(response.getPageInfo().getTotalCount()).isEqualTo(0);
    assertThat(response.getPageInfo().getTotalPages()).isEqualTo(0);
  }

  @Test
  void retrieveShortLinks_pagination_returnsCorrectPagesAndMetadata() {
    List<ShortLink> allLinks = new ArrayList<>();
    for (int i = 5; i >= 1; i--) {
      allLinks.add(
          ShortLink.newBuilder()
              .setShortCode("code" + i)
              .setLongUrl("https://example.com/" + i)
              .setCreatedAt(1000L * i)
              .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
              .build());
    }
    List<ShortLinkEntity> allEntities =
        allLinks.stream().map(ShortLinkEntity::fromProto).toList();

    when(repository.count()).thenReturn(5L);
    when(repository.listPaged(0, 2)).thenReturn(allEntities.subList(0, 2));
    when(repository.listPaged(2, 2)).thenReturn(allEntities.subList(2, 4));
    when(repository.listPaged(4, 2)).thenReturn(allEntities.subList(4, 5));
    when(repository.listPaged(6, 2)).thenReturn(List.of());

    // Page 1
    RetrieveShortLinksResponse page1 = linkService.retrieveShortLinks(1, 2);
    assertThat(page1.getLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code5", "code4");
    assertThat(page1.getPageInfo().getPage()).isEqualTo(1);
    assertThat(page1.getPageInfo().getPageSize()).isEqualTo(2);
    assertThat(page1.getPageInfo().getTotalCount()).isEqualTo(5);
    assertThat(page1.getPageInfo().getTotalPages()).isEqualTo(3);

    // Page 2
    RetrieveShortLinksResponse page2 = linkService.retrieveShortLinks(2, 2);
    assertThat(page2.getLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code3", "code2");
    assertThat(page2.getPageInfo().getPage()).isEqualTo(2);

    // Page 3
    RetrieveShortLinksResponse page3 = linkService.retrieveShortLinks(3, 2);
    assertThat(page3.getLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code1");
    assertThat(page3.getPageInfo().getPage()).isEqualTo(3);

    // Page 4 (out of bounds)
    RetrieveShortLinksResponse page4 = linkService.retrieveShortLinks(4, 2);
    assertThat(page4.getLinksList()).isEmpty();
    assertThat(page4.getPageInfo().getPage()).isEqualTo(4);
    assertThat(page4.getPageInfo().getTotalCount()).isEqualTo(5);
    assertThat(page4.getPageInfo().getTotalPages()).isEqualTo(3);
  }

  @Test
  void retrieveShortLinks_negativePage_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.retrieveShortLinks(-1, 10))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("page must be non-negative");
  }

  @Test
  void retrieveShortLinks_negativePageSize_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.retrieveShortLinks(1, -1))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("page_size must be non-negative");
  }

  @Test
  void retrieveShortLinks_pageSizeCappedAtMax() {
    RetrieveShortLinksResponse response = linkService.retrieveShortLinks(1, 200);
    assertThat(response.getPageInfo().getPageSize()).isEqualTo(100);
  }
}
