package com.example.shortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.ListShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.link.exception.InvalidArgumentException;
import com.example.shortener.link.entity.ShortLinkEntity;
import com.example.shortener.link.exception.AuthorNotActiveException;
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
  private AuthorDirectory authorDirectory;

  private static final String AUTHOR = "11111111-2222-3333-4444-555555555555";

  private LinkService linkService;

  @BeforeEach
  void setUp() {
    linkService = new LinkService(repository, new ShortCodeGenerator(), authorDirectory);
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
    assertThatThrownBy(() -> linkService.createShortLink("", AUTHOR))
        .isInstanceOf(InvalidArgumentException.class);

    verifyNoInteractions(repository, authorDirectory);
  }

  @Test
  void createShortLink_authorNotActive_refusesBeforeGeneratingACode() {
    doThrow(new AuthorNotActiveException("author is USER_STATUS_SUSPENDED"))
        .when(authorDirectory).requireActiveAuthor(AUTHOR);

    assertThatThrownBy(() -> linkService.createShortLink("https://example.com", AUTHOR))
        .isInstanceOf(AuthorNotActiveException.class);

    verifyNoInteractions(repository);
  }

  @Test
  void createShortLink_stampsTheAuthorFromTheCallerNotTheRequest() {
    ShortLink link = linkService.createShortLink("https://anthropic.com", AUTHOR);

    assertThat(link.getAuthorId()).isEqualTo(AUTHOR);
    verify(authorDirectory).requireActiveAuthor(AUTHOR);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://malware.test",
      "https://phishing.test",
      "http://spam.test/some/path",
  })
  void createShortLink_blockedHost_throwsUrlFlaggedAndStoresNothing(String url) {
    assertThatThrownBy(() -> linkService.createShortLink(url, AUTHOR))
        .isInstanceOf(UrlFlaggedException.class)
        .hasMessageContaining("host is on the blocklist");

    // A flagged link must never reach the database.
    verifyNoInteractions(repository);
  }

  @Test
  void createShortLink_blocklistMatchesHostNotSubstring() {
    // "malware.test.example.com" merely CONTAINS a blocked host; the check is on the parsed
    // host, so this one is allowed through.
    ShortLink link = linkService.createShortLink("https://malware.test.example.com", AUTHOR);

    assertThat(link.getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);
    verify(repository).save(any(ShortLinkEntity.class));
  }

  @Test
  void createShortLink_cleanUrl_storesAndReturnsTheLink() {
    ShortLink link = linkService.createShortLink("https://anthropic.com", AUTHOR);

    assertThat(link.getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(link.getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);
    assertThat(link.getShortCode()).isNotEmpty();

    verify(repository).save(any(ShortLinkEntity.class));
  }

  // --- getShortLink tests -------------------------------------------------------

  @Test
  void getShortLink_existingCode_returnsTheLink() {
    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    when(repository.findById("abc1234")).thenReturn(Optional.of(ShortLinkEntity.fromProto(link)));

    ShortLink resolved = linkService.getShortLink("abc1234");
    assertThat(resolved.getLongUrl()).isEqualTo("https://anthropic.com");
    assertThat(resolved.getShortCode()).isEqualTo("abc1234");
  }

  @Test
  void getShortLink_unknownCode_throwsNotFoundStatus() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> linkService.getShortLink("nope"))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  // --- updateShortLink tests --------------------------------------------------------

  private ShortLinkEntity existingLink(String longUrl) {
    return ShortLinkEntity.fromProto(
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl(longUrl)
            .setCreatedAt(1000L)
            .setUpdatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build());
  }

  @Test
  void updateShortLink_unknownCode_throwsNotFound() {
    when(repository.findById("abc1234")).thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> linkService.updateShortLink("abc1234", "https://new.example.com", null))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void updateShortLink_longUrlOnly_leavesStatusAlone() {
    ShortLinkEntity entity = existingLink("https://old.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    // status == null is what the adapter passes when the client omitted the field.
    ShortLink result = linkService.updateShortLink("abc1234", "https://new.example.com", null);

    assertThat(result.getLongUrl()).isEqualTo("https://new.example.com");
    assertThat(result.getStatus()).isEqualTo(LinkStatus.LINK_STATUS_ACTIVE);
  }

  @Test
  void updateShortLink_statusOnly_leavesLongUrlAlone() {
    ShortLinkEntity entity = existingLink("https://old.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    ShortLink result =
        linkService.updateShortLink("abc1234", null, LinkStatus.LINK_STATUS_EXPIRED);

    assertThat(result.getStatus()).isEqualTo(LinkStatus.LINK_STATUS_EXPIRED);
    assertThat(result.getLongUrl()).isEqualTo("https://old.example.com");
  }

  @Test
  void updateShortLink_stampsUpdatedAtButNotCreatedAt() {
    ShortLinkEntity entity = existingLink("https://old.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    ShortLink result =
        linkService.updateShortLink("abc1234", null, LinkStatus.LINK_STATUS_EXPIRED);

    assertThat(result.getCreatedAt()).isEqualTo(1000L);
    assertThat(result.getUpdatedAt()).isGreaterThan(1000L);
  }

  @Test
  void updateShortLink_toBlockedHost_throwsUrlFlagged() {
    // The bypass this guards: create with a clean URL, then patch it to a blocked one.
    ShortLinkEntity entity = existingLink("https://clean.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> linkService.updateShortLink("abc1234", "https://malware.test", null))
        .isInstanceOf(UrlFlaggedException.class);

    assertThat(entity.getLongUrl()).isEqualTo("https://clean.example.com");
  }

  @Test
  void updateShortLink_clientSetsFlagged_throwsInvalidArgument() {
    ShortLinkEntity entity = existingLink("https://clean.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    assertThatThrownBy(
        () -> linkService.updateShortLink("abc1234", null, LinkStatus.LINK_STATUS_FLAGGED))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("set by the audit");
  }

  @Test
  void updateShortLink_invalidUrl_throwsInvalidArgument() {
    ShortLinkEntity entity = existingLink("https://clean.example.com");
    when(repository.findById("abc1234")).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> linkService.updateShortLink("abc1234", "not-a-url", null))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void updateShortLink_nothingSent_throwsInvalidArgumentWithoutTouchingTheDatabase() {
    assertThatThrownBy(() -> linkService.updateShortLink("abc1234", null, null))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("nothing to update");

    verifyNoInteractions(repository);
  }

  @Test
  void updateShortLink_blankShortCode_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.updateShortLink("", "https://x.example.com", null))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("short_code is required");
  }

  // --- listShortLinks tests -----------------------------------------------------

  @Test
  void listShortLinks_emptyStore_returnsEmptyListAndZeroPageInfo() {
    when(repository.count()).thenReturn(0L);
    when(repository.listPaged(0, 10)).thenReturn(List.of());

    ListShortLinksResponse response = linkService.listShortLinks(0, 0);

    assertThat(response.getShortLinksList()).isEmpty();
    assertThat(response.getPageInfo().getPage()).isEqualTo(1);
    assertThat(response.getPageInfo().getPageSize()).isEqualTo(10);
    assertThat(response.getPageInfo().getTotalCount()).isEqualTo(0);
    assertThat(response.getPageInfo().getTotalPages()).isEqualTo(0);
  }

  @Test
  void listShortLinks_pagination_returnsCorrectPagesAndMetadata() {
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
    ListShortLinksResponse page1 = linkService.listShortLinks(1, 2);
    assertThat(page1.getShortLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code5", "code4");
    assertThat(page1.getPageInfo().getPage()).isEqualTo(1);
    assertThat(page1.getPageInfo().getPageSize()).isEqualTo(2);
    assertThat(page1.getPageInfo().getTotalCount()).isEqualTo(5);
    assertThat(page1.getPageInfo().getTotalPages()).isEqualTo(3);

    // Page 2
    ListShortLinksResponse page2 = linkService.listShortLinks(2, 2);
    assertThat(page2.getShortLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code3", "code2");
    assertThat(page2.getPageInfo().getPage()).isEqualTo(2);

    // Page 3
    ListShortLinksResponse page3 = linkService.listShortLinks(3, 2);
    assertThat(page3.getShortLinksList())
        .extracting(ShortLink::getShortCode)
        .containsExactly("code1");
    assertThat(page3.getPageInfo().getPage()).isEqualTo(3);

    // Page 4 (out of bounds)
    ListShortLinksResponse page4 = linkService.listShortLinks(4, 2);
    assertThat(page4.getShortLinksList()).isEmpty();
    assertThat(page4.getPageInfo().getPage()).isEqualTo(4);
    assertThat(page4.getPageInfo().getTotalCount()).isEqualTo(5);
    assertThat(page4.getPageInfo().getTotalPages()).isEqualTo(3);
  }

  @Test
  void listShortLinks_negativePage_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.listShortLinks(-1, 10))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("page must be non-negative");
  }

  @Test
  void listShortLinks_negativePageSize_throwsInvalidArgument() {
    assertThatThrownBy(() -> linkService.listShortLinks(1, -1))
        .isInstanceOf(InvalidArgumentException.class)
        .hasMessageContaining("page_size must be non-negative");
  }

  @Test
  void listShortLinks_pageSizeCappedAtMax() {
    ListShortLinksResponse response = linkService.listShortLinks(1, 200);
    assertThat(response.getPageInfo().getPageSize()).isEqualTo(100);
  }
}
