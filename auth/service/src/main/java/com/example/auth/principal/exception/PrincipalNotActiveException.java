package com.example.auth.principal.exception;

import com.example.auth.principal.entity.PrincipalStatusEnum;

/**
 * The credentials were CORRECT but the principal may not act. Deliberately distinct from
 * {@link InvalidCredentialsException}: that one means "we don't believe you are who you say",
 * this one means "we believe you, and the answer is still no".
 */
public class PrincipalNotActiveException extends RuntimeException {

  private final PrincipalStatusEnum status;

  public PrincipalNotActiveException(PrincipalStatusEnum status) {
    super("principal is " + status);
    this.status = status;
  }

  public PrincipalStatusEnum getStatus() {
    return status;
  }
}
