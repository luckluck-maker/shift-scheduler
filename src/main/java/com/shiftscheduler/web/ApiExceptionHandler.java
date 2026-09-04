package com.shiftscheduler.web;

import com.shiftscheduler.assignment.AssignmentRejectedException;
import com.shiftscheduler.assignment.AssignmentRejection;
import com.shiftscheduler.auth.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

// Turns the exceptions the services throw into one shape of error response.
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // A wrong username or password. 401.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getCode());
    }

    // 404.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // A rule the caller broke, or two requests clashing. 409.
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode());
    }

    // Input the rules refuse. 400.
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidationRule(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode());
    }

    // Carries the broken rules, so the screen can offer to override them.
    @ExceptionHandler(AssignmentRejectedException.class)
    public ResponseEntity<AssignmentRejection> handleAssignmentRejected(
            AssignmentRejectedException ex) {

        AssignmentRejection body = new AssignmentRejection(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                ex.getBlocking(),
                ex.getOverridable());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Bean validation on the request body.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                // More than one field can fail at once, and the response has room
                // for one message, so they are joined.
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, details, null);
    }

    // Malformed JSON, a missing parameter, or the wrong type. 400.
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex) {
        // The exception message names internal fields, so a fixed message goes back.
        return build(HttpStatus.BAD_REQUEST, "The request could not be read", null);
    }

    // One shape for every error, so the screen can always read the same fields.
    private ResponseEntity<ApiError> build(HttpStatus status, String message, String code) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message);

        return ResponseEntity.status(status).body(body);
    }

    // A unique key the code checked for and lost the race on. 409.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDuplicate(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT,
                "That record already exists. Reload and try again.", ErrorCode.DUPLICATE);
    }

    // Two requests arrived together. Both read the same version, both passed
    // requireVersion, and @Version failed the second one at commit.
    // Answered as STALE_VERSION, the same code requireVersion returns, because
    // either way the manager has to reload and try again.
    // Says "record" and not "schedule", since employees and constraints have a
    // version too.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLostRace(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "This record was changed by someone else. Reload and try again.",
                ErrorCode.STALE_VERSION);
    }

    // The role isn't allowed to do this. 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You are not allowed to do that", null);
    }

    // Anything that wasn't handled above.
    // Spring's own exceptions (404 for an unknown path, 405) keep their status.
    // The rest is a 500 and goes to the log with the full stack trace.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            return build(status, status.getReasonPhrase(), null);
        }

        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", null);
    }
}