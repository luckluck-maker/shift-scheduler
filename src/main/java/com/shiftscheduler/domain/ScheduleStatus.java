package com.shiftscheduler.domain;

// COLLECTING -> DRAFT -> SOLVING -> PUBLISHED. Published is the end.
public enum ScheduleStatus {
    COLLECTING,
    DRAFT,
    SOLVING,
    PUBLISHED
}