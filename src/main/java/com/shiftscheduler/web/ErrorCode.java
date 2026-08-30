package com.shiftscheduler.web;

// Error codes only for cases where the screen needs to react
// differently to two failures that share a status code.
public final class ErrorCode {

    public static final String LAST_MANAGER = "LAST_MANAGER";
    public static final String STALE_VERSION = "STALE_VERSION";
    public static final String WRONG_STATUS = "WRONG_STATUS";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String SHIFT_ASSIGNED = "SHIFT_ASSIGNED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String NO_SHIFT_TYPES = "NO_SHIFT_TYPES";

    private ErrorCode() {
    }
}