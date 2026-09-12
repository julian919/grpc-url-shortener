-- Lifecycle state for an identity: ACTIVE principals may be issued tokens, others may not.
-- Existing rows are all live identities, so they default to ACTIVE and the column can be NOT NULL.
--
-- Enforced at every mint point (login, client credentials, refresh), not per request -- see
-- PrincipalService. Suspending someone also revokes their refresh tokens, so the longest they can
-- keep acting is the remaining life of an access token already in their hands (15 minutes).
ALTER TABLE principals ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE';
