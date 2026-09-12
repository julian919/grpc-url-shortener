package com.example.shortener.link;

/**
 * The seam over "is this author allowed to create links?" -- same shape as user-service's own
 * PrincipalDirectory. Keeps {@link LinkService} free of gRPC types and makes the rule unit
 * testable without a server.
 */
public interface AuthorDirectory {

  /**
   * @throws com.example.shortener.link.exception.AuthorNotActiveException if the author exists but
   *     may not act
   */
  void requireActiveAuthor(String principalId);
}
