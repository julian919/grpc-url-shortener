package com.example.auth.principal.entity;

/**
 * Lifecycle state of an identity, independent of {@link PrincipalTypeEnum} (which says WHAT a
 * principal is, not whether it may currently act).
 *
 * <p>Checked wherever a token is minted -- login, client credentials, and refresh -- so a
 * non-ACTIVE principal cannot obtain new credentials. See {@code PrincipalService}.
 */
public enum PrincipalStatusEnum {
  /** May authenticate and be issued tokens. The default for every new principal. */
  ACTIVE,
  /** Temporarily barred. Reversible: flipping back to ACTIVE restores access. */
  SUSPENDED,
  /** Closed by the owner or an administrator. Terminal in practice. */
  DEACTIVATED
}
