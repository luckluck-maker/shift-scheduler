package com.shiftscheduler.web;

// No such id. 404.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}