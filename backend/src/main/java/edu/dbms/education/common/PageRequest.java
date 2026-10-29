package edu.dbms.education.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Reusable pagination parameters injected as query-string arguments.
 *
 * DBMS concept: LIMIT / OFFSET pagination.
 * LIMIT controls the page size; OFFSET = page * size skips earlier rows.
 * All list queries in repositories append:
 *   " LIMIT ? OFFSET ?"  with (size, page * size) as bind values.
 *
 * Defaults: page=0, size=20, maxSize=100.
 */
@Getter
@Setter
public class PageRequest {

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    /** Calculates the OFFSET bind value: page × size. */
    public int offset() {
        return page * size;
    }
}
