package com.example.auth.principal.exception;

public class LoginAlreadyRegisteredException extends RuntimeException {

  public LoginAlreadyRegisteredException(String email) {
    super("An account already exists for " + email);
  }
}
