package com.example.auth.principal.entity;

/**
 * USER principals log in with a password (email today). SERVICE principals
 * authenticate
 * with a client id/secret to call internal RPCs -- see
 * {@link AuthProviderEnum#CLIENT_ID}.
 */
public enum PrincipalTypeEnum {
  USER,
  SERVICE
}
