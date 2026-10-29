package edu.dbms.education.console;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request body for POST /api/console/select.
 *
 * sql must be a single SELECT statement. The validator in SqlConsoleService
 * enforces this at the application layer; the DB enforces it implicitly
 * because app_user has no DDL rights.
 *
 * params are positional bind values for ? placeholders in the SQL.
 * Using bind values is mandatory – raw string concatenation is never done.
 */
@Getter
@Setter
public class ConsoleRequest {

    @NotBlank(message = "sql is required")
    private String       sql;

    /** Optional positional bind values for ? placeholders. */
    private List<Object> params;
}
