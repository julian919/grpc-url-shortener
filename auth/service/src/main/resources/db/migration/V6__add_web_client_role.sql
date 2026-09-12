-- Standardises service clients onto roles. Before this, user-service got its grant through a role
-- (SERVICE_INTERNAL) while publicweb carried LIST_SHORT_URL as a direct permission -- two
-- mechanisms for the same kind of principal. JwtService.effectivePermissions() unions both, so the
-- distinction was invisible at runtime and therefore arbitrary. Direct `permissions` stays as the
-- documented escape hatch for a genuine one-off grant.
--
-- The role CATALOG is environment-agnostic, so it belongs here and runs everywhere. Which
-- principal HOLDS the role is environment-specific and lives in db/dev (dev) or is assigned out of
-- band (real environments) -- see db/README.md.
--
-- One role per KIND of client, not per client instance: publicweb is the first browser frontend
-- but not the last, and a second one (an admin console, a mobile web app) takes WEB_CLIENT
-- unchanged. Contrast USER_SERVICE in V7, which is per-workload precisely because each backend
-- service needs a different, minimal permission set.
INSERT INTO roles (name, permissions)
VALUES ('WEB_CLIENT', ARRAY['LIST_SHORT_URL'])
ON CONFLICT (name) DO NOTHING;

-- Move any principal already holding the direct grant onto the role. Grant-preserving: the role
-- expands to exactly the permission being removed, so effective permissions are unchanged and no
-- token loses access. Runs in every environment because it repairs data V3-era config created.
UPDATE principals
SET roles       = array_append(roles, 'WEB_CLIENT'),
    permissions = array_remove(permissions, 'LIST_SHORT_URL')
WHERE type = 'SERVICE'
  AND 'LIST_SHORT_URL' = ANY (permissions)
  AND NOT ('WEB_CLIENT' = ANY (roles));
