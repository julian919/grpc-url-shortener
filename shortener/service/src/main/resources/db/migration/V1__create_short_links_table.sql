CREATE TABLE short_links (
  short_code  VARCHAR(64) PRIMARY KEY,
  long_url    TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL,
  status      VARCHAR(32) NOT NULL
);

CREATE INDEX idx_short_links_created_at ON short_links (created_at DESC, short_code ASC);
