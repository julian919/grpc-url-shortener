package com.example.auth.principal.entity;

/**
 * EMAIL identifies a USER principal (a person's login). CLIENT_ID identifies a
 * SERVICE
 * principal (a client-credentials caller like user-service) -- it shares this
 * enum because
 * both describe "what kind of identifier", not "what kind of grant".
 */
public enum AuthProviderEnum {
  EMAIL,
  CLIENT_ID
}
