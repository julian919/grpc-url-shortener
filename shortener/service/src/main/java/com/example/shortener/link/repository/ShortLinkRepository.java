package com.example.shortener.link.repository;

import com.example.shortener.link.entity.ShortLinkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLinkEntity, String> {

  /**
   * One page by offset. Cost grows with {@code offset}, which is why LinkService only lets a caller
   * page through the first 10,000 rows (MAX_RESULT_WINDOW). Uses idx_short_links_created_at.
   */
  @Query(
      value =
          "SELECT * FROM short_links ORDER BY created_at DESC, short_code ASC LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  List<ShortLinkEntity> listPaged(@Param("offset") int offset, @Param("limit") int limit);

  /**
   * Counts rows, but stops at {@code limit}. The inner LIMIT lets Postgres quit as soon as it has
   * found that many, so this reads at most {@code limit} rows however large the table grows --
   * unlike count(*), which scans all of them on every call.
   */
  @Query(
      value = "SELECT count(*) FROM (SELECT 1 FROM short_links LIMIT :limit) AS bounded",
      nativeQuery = true)
  long countUpTo(@Param("limit") int limit);
}
