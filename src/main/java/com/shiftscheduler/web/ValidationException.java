package com.shiftscheduler.web;

public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}