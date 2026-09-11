package com.example.auth.token.exception;

/**
 * An already-rotated (revoked) refresh token was presented again: OAuth 2.1 reuse detection.
 * By the time this is thrown, every refresh token for the principal has been revoked, which is
 * why {@code RefreshTokenService.rotateRefreshToken} must not roll back on it.
 */
public class RefreshTokenRevokedException extends RuntimeException {

  public RefreshTokenRevokedException() {
    super("Revoked refresh token presented");
  }
}
