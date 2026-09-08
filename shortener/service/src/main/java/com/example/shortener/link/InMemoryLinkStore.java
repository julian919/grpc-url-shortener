package com.example.shortener.link;

import com.example.shortener.api.ShortLink;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Volatile, per-replica storage -- data is lost on restart and not shared across replicas.
 * That's a known, deliberate gap in this demo (see the README), not an oversight: it's the
 * reason read-scaling this service would need a real, shared store before it actually worked.
 */
@Component
public class InMemoryLinkStore implements LinkStore {

  private final Map<String, ShortLink> store = new ConcurrentHashMap<>();

  @Override
  public void save(ShortLink link) {
    store.put(link.getShortCode(), link);
  }

  @Override
  public Optional<ShortLink> findByCode(String shortCode) {
    return Optional.ofNullable(store.get(shortCode));
  }
}
