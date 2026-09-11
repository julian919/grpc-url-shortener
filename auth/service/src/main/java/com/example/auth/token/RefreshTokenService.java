package com.example.auth.token;

import com.example.auth.api.OAuth2Token;
import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.token.entity.RefreshTokenEntity;
import com.example.auth.token.exception.RefreshTokenExpiredException;
import com.example.auth.token.exception.RefreshTokenInvalidException;
import com.example.auth.token.exception.RefreshTokenMissingException;
import com.example.auth.token.exception.RefreshTokenRevokedException;
import com.example.auth.token.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final Duration refreshTokenTtl;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      JwtService jwtService,
      @Value("${app.jwt.refresh-token-ttl:168h}") Duration refreshTokenTtl) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtService = jwtService;
    this.refreshTokenTtl = refreshTokenTtl;
  }

  @Transactional
  public String issueRefreshToken(PrincipalEntity principal) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = hashToken(rawToken);
    Instant expiresAt = Instant.now().plus(refreshTokenTtl);

    RefreshTokenEntity entity = new RefreshTokenEntity(
        UUID.randomUUID(),
        principal,
        tokenHash,
        expiresAt);
    refreshTokenRepository.save(entity);

    return rawToken;
  }

  // noRollbackFor: the revoked-token branch revokes every token for the principal and THEN
  // throws. Without this, the default rollback-on-RuntimeException undid that revoke-all, so
  // reuse detection never actually revoked anything (verified live: after a replayed token was
  // rejected, the attacker-side rotated token still renewed successfully).
  @Transactional(noRollbackFor = RefreshTokenRevokedException.class)
  public OAuth2Token rotateRefreshToken(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new RefreshTokenMissingException();
    }

    String tokenHash = hashToken(rawRefreshToken);
    RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(RefreshTokenInvalidException::new);

    PrincipalEntity principal = entity.getPrincipal();

    // Security best practice: token reuse detection (OAuth 2.1)
    if (entity.isRevoked()) {
      log.warn("Security Alert: Attempted reuse of revoked refresh token for principal {}", principal.getId());
      refreshTokenRepository.revokeAllForPrincipal(principal);
      throw new RefreshTokenRevokedException();
    }

    if (entity.isExpired()) {
      throw new RefreshTokenExpiredException();
    }

    // Mark previous token as revoked
    entity.revoke();

    // Issue newly rotated refresh token and new access token
    String newRawRefreshToken = issueRefreshToken(principal);
    String newAccessToken = jwtService.issueAccessToken(principal);

    return OAuth2Token.newBuilder()
        .setAccessToken(newAccessToken)
        .setTokenType("bearer")
        .setExpiresInSeconds(jwtService.accessTokenTtl().toSeconds())
        .setRefreshToken(newRawRefreshToken)
        .build();
  }

  public Duration getRefreshTokenTtl() {
    return refreshTokenTtl;
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
