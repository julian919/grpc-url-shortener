package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.PageInfo;
import com.example.shortener.api.ResolveShortLinkRequest;
import com.example.shortener.api.ResolveShortLinkResponse;
import com.example.shortener.api.RetrieveShortLinksRequest;
import com.example.shortener.api.RetrieveShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.link.LinkService;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortenerGrpcServiceTest {

  @Mock
  private LinkService linkService;
  @Mock
  private StreamObserver<CreateShortLinkResponse> createObserver;
  @Mock
  private StreamObserver<ResolveShortLinkResponse> resolveObserver;
  @Mock
  private StreamObserver<RetrieveShortLinksResponse> retrieveObserver;

  private ShortenerGrpcService service;

  @BeforeEach
  void setUp() {
    service = new ShortenerGrpcService(linkService);
  }

  @Test
  void createShortLink_delegatesToLinkService_andSendsResponse() {
    CreateShortLinkRequest request =
        CreateShortLinkRequest.newBuilder().setLongUrl("https://anthropic.com").build();

    ShortLink expectedLink =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    when(linkService.createShortLink("https://anthropic.com")).thenReturn(expectedLink);

    service.createShortLink(request, createObserver);

    ArgumentCaptor<CreateShortLinkResponse> captor =
        ArgumentCaptor.forClass(CreateShortLinkResponse.class);
    verify(createObserver).onNext(captor.capture());
    verify(createObserver).onCompleted();

    assertThat(captor.getValue().getLink()).isEqualTo(expectedLink);
  }

  @Test
  void resolveShortLink_delegatesToLinkService_andSendsResponse() {
    ResolveShortLinkRequest request =
        ResolveShortLinkRequest.newBuilder().setShortCode("abc1234").build();

    ShortLink expectedLink =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    when(linkService.resolveShortLink("abc1234")).thenReturn(expectedLink);

    service.resolveShortLink(request, resolveObserver);

    ArgumentCaptor<ResolveShortLinkResponse> captor =
        ArgumentCaptor.forClass(ResolveShortLinkResponse.class);
    verify(resolveObserver).onNext(captor.capture());
    verify(resolveObserver).onCompleted();

    assertThat(captor.getValue().getLink()).isEqualTo(expectedLink);
  }

  @Test
  void retrieveShortLinks_delegatesToLinkService_andSendsResponse() {
    RetrieveShortLinksRequest request =
        RetrieveShortLinksRequest.newBuilder().setPage(1).setPageSize(10).build();

    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    RetrieveShortLinksResponse expectedResponse =
        RetrieveShortLinksResponse.newBuilder()
            .addAllLinks(List.of(link))
            .setPageInfo(
                PageInfo.newBuilder()
                    .setPage(1)
                    .setPageSize(10)
                    .setTotalCount(1)
                    .setTotalPages(1)
                    .build())
            .build();

    when(linkService.retrieveShortLinks(1, 10)).thenReturn(expectedResponse);

    service.retrieveShortLinks(request, retrieveObserver);

    ArgumentCaptor<RetrieveShortLinksResponse> captor =
        ArgumentCaptor.forClass(RetrieveShortLinksResponse.class);
    verify(retrieveObserver).onNext(captor.capture());
    verify(retrieveObserver).onCompleted();

    assertThat(captor.getValue()).isEqualTo(expectedResponse);
  }
}
