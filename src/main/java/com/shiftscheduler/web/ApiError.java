package com.shiftscheduler.web;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,

        // Set only where the client has to tell two failures apart. Left out of
        // the response when there is nothing to distinguish.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String code,

        String message
) {
}