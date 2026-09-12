package com.example.shortener.link.exception;

/**
 * The caller's token was valid, but user-service says the author is not in good standing. A JWT
 * stays valid for its full lifetime after a user is suspended, so the token alone cannot answer
 * this -- which is the whole reason for the lookup.
 */
public class AuthorNotActiveException extends RuntimeException {

  public AuthorNotActiveException(String message) {
    super(message);
  }
}
