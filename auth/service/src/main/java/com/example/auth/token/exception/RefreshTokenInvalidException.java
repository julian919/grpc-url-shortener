package com.example.auth.token.exception;

/** The presented refresh token matches no stored token hash. */
public class RefreshTokenInvalidException extends RuntimeException {

  public RefreshTokenInvalidException() {
    super("Invalid refresh token");
  }
}
