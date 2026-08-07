package com.shiftscheduler.web;

// Error codes only for cases where the screen needs to react
// differently to two failures that share a status code.
public final class ErrorCode {

    public static final String LAST_MANAGER = "LAST_MANAGER";
    public static final String STALE_VERSION = "STALE_VERSION";
    public static final String WRONG_STATUS = "WRONG_STATUS";

    private ErrorCode() {
    }
}