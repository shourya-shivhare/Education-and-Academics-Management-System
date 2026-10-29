package edu.dbms.education.api;

import edu.dbms.education.common.ConflictException;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Central exception handler – translates exceptions into the ResponseEnvelope
 * error format and maps MySQL SIGNAL errors to HTTP 409 Conflict.
 *
 * DBMS concept: MySQL stored procedures use SIGNAL SQLSTATE '45000' to raise
 * application-defined errors (e.g. "enrollment already exists", "capacity full").
 * SQLSTATE '45000' is the portable user-defined class; procedures set the
 * MESSAGE_TEXT condition item to a human-readable message.
 *
 * Spring wraps MySQL SIGNAL errors as UncategorizedSQLException containing a
 * java.sql.SQLException with SQLState '45000'.  This handler extracts the
 * message and returns HTTP 409 so the frontend can display it directly.
 *
 * Error code mapping:
 *   SQLSTATE 45000 (MySQL SIGNAL) → 409 Conflict  + code "BUSINESS_RULE"
 *   DataIntegrityViolation (FK/UK) → 409 Conflict  + code "DATA_INTEGRITY"
 *   Validation failures            → 400 Bad Request + code "VALIDATION"
 *   Any other exception            → 500 Internal Server Error
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String MYSQL_SIGNAL_SQLSTATE = "45000";

    private final SqlTraceService traceService;

    // ── Not found ─────────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorEnvelope("NOT_FOUND", ex.getMessage(), traceService.getEntries()));
    }

    // ── Optimistic lock conflict ───────────────────────────────────────────

    /**
     * DBMS concept: optimistic concurrency control.
     * Thrown by MarksRepository when UPDATE ... WHERE version = ? affects 0 rows.
     * A concurrent writer already committed — caller should retry with fresh data.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorEnvelope("OPTIMISTIC_LOCK", ex.getMessage(),
                        traceService.getEntries()));
    }

    // ── Authentication failures ────────────────────────────────────────────

    /**
     * BadCredentialsException: wrong password or unknown username.
     *
     * DaoAuthenticationProvider deliberately converts UsernameNotFoundException
     * into BadCredentialsException so callers cannot distinguish "user not found"
     * from "wrong password" – prevents user-enumeration attacks.
     *
     * The trace IS included here because the SELECT query ran before the
     * password check failed (it's already in the trace list).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorEnvelope> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorEnvelope(
                        "UNAUTHORIZED",
                        "Invalid username or password.",
                        traceService.getEntries()));
    }

    /**
     * DisabledException: user exists but is_active = FALSE.
     * Returns 401 (not 403) – the user cannot authenticate at all.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorEnvelope> handleDisabled(DisabledException ex) {
        log.warn("Disabled account login attempt");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorEnvelope(
                        "ACCOUNT_DISABLED",
                        "This account has been disabled.",
                        traceService.getEntries()));
    }

    /**
     * AccessDeniedException from @PreAuthorize – valid token, wrong role.
     * The SecurityConfig AccessDeniedHandler handles pre-controller 403s;
     * this handler catches any that slip through to controller advice.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorEnvelope(
                        "FORBIDDEN",
                        "You do not have permission to access this resource.",
                        traceService.getEntries()));
    }

    // ── MySQL SIGNAL SQLSTATE '45000' ──────────────────────────────────────

    /**
     * UncategorizedSQLException is what Spring throws when the JDBC driver
     * reports an error that doesn't map to a known Spring DataAccessException
     * subclass.  MySQL SIGNALs with SQLSTATE 45000 land here.
     */
    @ExceptionHandler(UncategorizedSQLException.class)
    public ResponseEntity<ErrorEnvelope> handleUncategorizedSql(UncategorizedSQLException ex) {
        SQLException sqlEx = ex.getSQLException();
        if (MYSQL_SIGNAL_SQLSTATE.equals(sqlEx.getSQLState())) {
            // Extract the MESSAGE_TEXT set in the stored procedure's SIGNAL
            log.warn("MySQL SIGNAL 45000: {}", sqlEx.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorEnvelope(
                            "BUSINESS_RULE",
                            sqlEx.getMessage(),
                            traceService.getEntries()));
        }
        // Some other uncategorized SQL error – treat as 500
        log.error("Uncategorized SQL exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorEnvelope(
                        "SQL_ERROR",
                        "An unexpected database error occurred.",
                        traceService.getEntries()));
    }

    // ── Constraint violations (FK, UNIQUE) ────────────────────────────────

    /**
     * DataIntegrityViolationException covers:
     *   - FOREIGN KEY constraint failures (ER_ROW_IS_REFERENCED, ER_NO_REFERENCED_ROW)
     *   - UNIQUE KEY / PRIMARY KEY duplicate entries (ER_DUP_ENTRY)
     *
     * DBMS concept: declarative integrity rules enforce correctness at the
     * storage layer regardless of application code.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorEnvelope(
                        "DATA_INTEGRITY",
                        ex.getMostSpecificCause().getMessage(),
                        traceService.getEntries()));
    }

    // ── Bean Validation (@Valid on request bodies) ─────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failure: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorEnvelope("VALIDATION", message, traceService.getEntries()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorEnvelope("VALIDATION", message, traceService.getEntries()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorEnvelope("INVALID_ARGUMENT", ex.getMessage(),
                        traceService.getEntries()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorEnvelope(
                        "INTERNAL",
                        "An unexpected error occurred.",
                        traceService.getEntries()));
    }
}
