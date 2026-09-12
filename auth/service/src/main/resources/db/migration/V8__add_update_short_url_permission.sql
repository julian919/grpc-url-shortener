-- UpdateShortLink is gated on UPDATE_SHORT_URL. Signed-in users own the links they create, so the
-- USER role gains it alongside CREATE_SHORT_URL.
--
-- The role CATALOG is environment-agnostic, so it belongs here and runs everywhere; which
-- principal holds USER is environment-specific -- see db/README.md.
UPDATE roles
SET permissions = array_append(permissions, 'UPDATE_SHORT_URL')
WHERE name = 'USER'
  AND NOT ('UPDATE_SHORT_URL' = ANY (permissions));
