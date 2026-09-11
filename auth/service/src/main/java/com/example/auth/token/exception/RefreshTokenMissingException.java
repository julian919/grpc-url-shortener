package com.example.auth.token.exception;

public class RefreshTokenMissingException extends RuntimeException {

  public RefreshTokenMissingException() {
    super("Refresh token is required");
  }
}
