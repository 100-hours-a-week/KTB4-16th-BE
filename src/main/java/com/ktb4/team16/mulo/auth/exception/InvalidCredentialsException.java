package com.ktb4.team16.mulo.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid login credentials");
    }
}
