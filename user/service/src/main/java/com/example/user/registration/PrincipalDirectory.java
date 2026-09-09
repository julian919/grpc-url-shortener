package com.example.user.registration;

import java.util.UUID;

/** The seam a gRPC-backed implementation sits behind -- directly mirrors bookstore-api's own
 * PrincipalDirectory interface, just over gRPC instead of HTTP. */
public interface PrincipalDirectory {

  UUID createPrincipal(String email, String password);
}
