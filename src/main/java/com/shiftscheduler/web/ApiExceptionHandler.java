package com.shiftscheduler.web;

import com.shiftscheduler.assignment.AssignmentRejectedException;
import com.shiftscheduler.assignment.AssignmentRejection;
import com.shiftscheduler.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidationRule(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode());
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, details, null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "The request could not be read", null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String code) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message);

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDuplicate(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT,
                "That record already exists. Reload and try again.", ErrorCode.DUPLICATE);
    }

    // Two requests arrived together, both read the same version, and both got
    // past the check at the top of the service. Only one of them can win the
    // update, and this is the other one. The version guard reports the same
    // thing when the client is simply out of date, so the screen already knows
    // what to do with it - without this it came back as a 500 and the user was
    // told the action failed rather than to reload.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLostRace(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "The schedule was changed by someone else. Reload and try again.",
                ErrorCode.STALE_VERSION);
    }
}