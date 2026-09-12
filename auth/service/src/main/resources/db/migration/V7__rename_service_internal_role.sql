-- SERVICE_INTERNAL -> USER_SERVICE.
--
-- SERVICE_INTERNAL was named as a shared bucket for "any internal service", which stops working as
-- soon as there is more than one: every service holding it would inherit CREATE_PRINCIPAL, whether
-- or not it has any business creating principals. One role per workload keeps each service's grant
-- minimal and independent -- the same reason AWS and GCP give each service account its own policy
-- rather than a shared one.
--
-- V3__seed_user_service_client.sql created SERVICE_INTERNAL and cannot be edited (Flyway checksums
-- applied migrations), so the rename happens here as a data migration.
--
-- Grant-preserving: the new role carries the old role's exact permission set, so no token gains or
-- loses access. The literal is only a fallback for a database where V3's role was already removed.
INSERT INTO roles (name, permissions)
SELECT 'USER_SERVICE',
       COALESCE((SELECT permissions FROM roles WHERE name = 'SERVICE_INTERNAL'),
                ARRAY['CREATE_PRINCIPAL'])
ON CONFLICT (name) DO NOTHING;

UPDATE principals
SET roles = array_replace(roles, 'SERVICE_INTERNAL', 'USER_SERVICE')
WHERE 'SERVICE_INTERNAL' = ANY (roles);

DELETE FROM roles WHERE name = 'SERVICE_INTERNAL';
