package com.example.shortener;

/** The request failed validation before any downstream call was made. */
class InvalidArgumentException extends RuntimeException {

  InvalidArgumentException(String message) {
    super(message);
  }
}
