package com.example.user.shared.entity;

/**
 * Business standing of a user, owned by this service.
 *
 * <p>Distinct from auth-service's {@code PrincipalStatusEnum}, which governs whether an identity
 * may be issued CREDENTIALS at all. This one governs whether a user in good standing may act --
 * the question a downstream service like shortener asks before accepting a write.
 */
public enum UserStatusEnum {
  ACTIVE,
  SUSPENDED,
  DEACTIVATED
}
