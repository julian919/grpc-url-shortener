package com.example.shortener.link.repository;

import com.example.shortener.link.entity.ShortLinkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLinkEntity, String> {

  @Query(
      value =
          "SELECT * FROM short_links ORDER BY created_at DESC, short_code ASC LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  List<ShortLinkEntity> listPaged(@Param("offset") int offset, @Param("limit") int limit);
}
