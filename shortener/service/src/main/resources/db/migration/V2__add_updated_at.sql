-- updated_at accompanies UpdateShortLink. Backfilled from created_at so existing rows get a
-- sensible value rather than NULL: a link never updated has been "last changed" at creation.
ALTER TABLE short_links ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE short_links SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE short_links ALTER COLUMN updated_at SET NOT NULL;
