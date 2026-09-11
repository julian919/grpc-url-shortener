package com.example.auth.token.exception;

public class RefreshTokenExpiredException extends RuntimeException {

  public RefreshTokenExpiredException() {
    super("Refresh token expired");
  }
}
