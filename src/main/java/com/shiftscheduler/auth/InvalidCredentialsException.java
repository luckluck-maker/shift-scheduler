package com.shiftscheduler.auth;

// Login refused. Answered as 401.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}