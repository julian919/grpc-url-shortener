-- Who created each link. Taken from the caller's JWT subject at creation time, never from the
-- request body. Nullable because rows created before this column existed have no author -- there
-- is no correct value to invent for them.
ALTER TABLE short_links ADD COLUMN author_id uuid;

CREATE INDEX idx_short_links_author_id ON short_links (author_id);
