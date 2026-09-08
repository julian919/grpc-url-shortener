package com.example.shortener.link;

import com.example.shortener.api.ShortLink;
import java.util.Optional;

/**
 * Storage for created links, kept separate from {@code ShortenerGrpcService} so the business
 * logic never depends on a concrete storage technology. {@link InMemoryLinkStore} is the only
 * implementation today; a Postgres-backed one is a drop-in replacement later, with nothing
 * above this interface needing to change.
 */
public interface LinkStore {

  void save(ShortLink link);

  Optional<ShortLink> findByCode(String shortCode);
}
