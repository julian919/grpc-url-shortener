-- Register is no longer anonymous: user-service gates it on REGISTER, so a front end must present
-- its client token to create an account. WEB_CLIENT is the role every browser front end holds, so
-- the permission goes there rather than to one named client.
--
-- Role CATALOG only -- environment-agnostic, so it runs everywhere. Which principal holds
-- WEB_CLIENT is environment-specific; see db/README.md.
UPDATE roles
SET permissions = array_append(permissions, 'REGISTER')
WHERE name = 'WEB_CLIENT'
  AND NOT ('REGISTER' = ANY (permissions));
