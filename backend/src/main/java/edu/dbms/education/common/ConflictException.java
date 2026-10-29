package edu.dbms.education.common;

/**
 * Thrown when an optimistic-lock UPDATE affects 0 rows, meaning another
 * concurrent writer already incremented the version.
 *
 * DBMS concept: optimistic concurrency control.
 * Instead of locking the row for the full duration of a read-modify-write
 * cycle, we stamp each row with a version integer. On UPDATE we assert:
 *   WHERE enrollment_id = ? AND offering_id = ? AND assessment_no = ? AND version = ?
 * If 0 rows are affected, someone else committed first.
 *
 * GlobalExceptionHandler maps this to HTTP 409 Conflict with code "OPTIMISTIC_LOCK".
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
