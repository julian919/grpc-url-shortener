package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.api.OAuth2Token;
import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.user.PrincipalEntity;
import com.example.auth.user.RefreshTokenEntity;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private JwtService jwtService;

  private RefreshTokenService refreshTokenService;

  private PrincipalEntity principal;

  @BeforeEach
  void setUp() {
    refreshTokenService = new RefreshTokenService(
        refreshTokenRepository,
        jwtService,
        Duration.ofDays(7));
    principal = new PrincipalEntity("secret-hash", Set.of("USER"));
  }

  @Test
  void issueRefreshToken_savesHashedEntityAndReturnsRawToken() {
    when(refreshTokenRepository.save(any(RefreshTokenEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    String rawToken = refreshTokenService.issueRefreshToken(principal);

    assertThat(rawToken).isNotBlank();
    verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
  }

  @Test
  void rotateRefreshToken_success_revokesOldAndReturnsNewTokens() {
    String rawToken = "raw-refresh-token-123";
    RefreshTokenEntity existing = new RefreshTokenEntity(
        UUID.randomUUID(),
        principal,
        "dummy-hash",
        Instant.now().plus(Duration.ofDays(1)));

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));
    when(jwtService.issueAccessToken(principal)).thenReturn("new-access-token");
    when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    when(refreshTokenRepository.save(any(RefreshTokenEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OAuth2Token response = refreshTokenService.rotateRefreshToken(rawToken);

    assertThat(existing.isRevoked()).isTrue();
    assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    assertThat(response.getTokenType()).isEqualTo("bearer");
    assertThat(response.getRefreshToken()).isNotBlank();
    assertThat(response.getRefreshToken()).isNotEqualTo(rawToken);
  }

  @Test
  void rotateRefreshToken_revokedToken_triggersReuseDetectionAndFails() {
    String rawToken = "reused-token";
    RefreshTokenEntity existing = new RefreshTokenEntity(
        UUID.randomUUID(),
        principal,
        "dummy-hash",
        Instant.now().plus(Duration.ofDays(1)));
    existing.revoke();

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Revoked refresh token presented");

    verify(refreshTokenRepository).revokeAllForPrincipal(principal);
  }

  @Test
  void rotateRefreshToken_expiredToken_fails() {
    String rawToken = "expired-token";
    RefreshTokenEntity existing = new RefreshTokenEntity(
        UUID.randomUUID(),
        principal,
        "dummy-hash",
        Instant.now().minus(Duration.ofMinutes(1)));

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Refresh token expired");
  }
}
