package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shortener.api.CreateShortLinkRequest;
import com.example.shortener.api.CreateShortLinkResponse;
import com.example.shortener.api.LinkStatus;
import com.example.shortener.api.PageInfo;
import com.example.shortener.api.GetShortLinkRequest;
import com.example.shortener.api.GetShortLinkResponse;
import com.example.shortener.api.ListShortLinksRequest;
import com.example.shortener.api.ListShortLinksResponse;
import com.example.shortener.api.ShortLink;
import com.example.shortener.link.LinkService;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ShortenerGrpcServiceTest {

  @Mock
  private LinkService linkService;
  @Mock
  private StreamObserver<CreateShortLinkResponse> createObserver;
  @Mock
  private StreamObserver<GetShortLinkResponse> resolveObserver;
  @Mock
  private StreamObserver<ListShortLinksResponse> retrieveObserver;

  private ShortenerGrpcService service;

  /** The principal id the adapter should read off the token's `sub` claim. */
  private static final String CALLER = "11111111-2222-3333-4444-555555555555";

  @BeforeEach
  void setUp() {
    service = new ShortenerGrpcService(linkService);

    // Spring Security's resource-server filter populates this before any rpc method runs. The
    // adapter reads `sub` from it rather than trusting anything in the request body.
    Jwt jwt = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(900),
        Map.of("alg", "RS256"), Map.of("sub", CALLER));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
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

    when(linkService.createShortLink("https://anthropic.com", CALLER)).thenReturn(expectedLink);

    service.createShortLink(request, createObserver);

    ArgumentCaptor<CreateShortLinkResponse> captor =
        ArgumentCaptor.forClass(CreateShortLinkResponse.class);
    verify(createObserver).onNext(captor.capture());
    verify(createObserver).onCompleted();

    assertThat(captor.getValue().getShortLink()).isEqualTo(expectedLink);
  }

  @Test
  void getShortLink_delegatesToLinkService_andSendsResponse() {
    GetShortLinkRequest request =
        GetShortLinkRequest.newBuilder().setShortCode("abc1234").build();

    ShortLink expectedLink =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    when(linkService.getShortLink("abc1234")).thenReturn(expectedLink);

    service.getShortLink(request, resolveObserver);

    ArgumentCaptor<GetShortLinkResponse> captor =
        ArgumentCaptor.forClass(GetShortLinkResponse.class);
    verify(resolveObserver).onNext(captor.capture());
    verify(resolveObserver).onCompleted();

    assertThat(captor.getValue().getShortLink()).isEqualTo(expectedLink);
  }

  @Test
  void listShortLinks_delegatesToLinkService_andSendsResponse() {
    ListShortLinksRequest request =
        ListShortLinksRequest.newBuilder().setPage(1).setPageSize(10).build();

    ShortLink link =
        ShortLink.newBuilder()
            .setShortCode("abc1234")
            .setLongUrl("https://anthropic.com")
            .setCreatedAt(1000L)
            .setStatus(LinkStatus.LINK_STATUS_ACTIVE)
            .build();

    ListShortLinksResponse expectedResponse =
        ListShortLinksResponse.newBuilder()
            .addAllShortLinks(List.of(link))
            .setPageInfo(
                PageInfo.newBuilder().setPage(1).setPageSize(10).setTotalCount(1).setTotalPages(1).build())
            .build();

    when(linkService.listShortLinks(1, 10)).thenReturn(expectedResponse);

    service.listShortLinks(request, retrieveObserver);

    ArgumentCaptor<ListShortLinksResponse> captor =
        ArgumentCaptor.forClass(ListShortLinksResponse.class);
    verify(retrieveObserver).onNext(captor.capture());
    verify(retrieveObserver).onCompleted();

    assertThat(captor.getValue()).isEqualTo(expectedResponse);
  }
}
