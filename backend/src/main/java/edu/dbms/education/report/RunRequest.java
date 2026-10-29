package edu.dbms.education.report;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request body for POST /api/reports/{id}/run.
 *
 * params is an ordered list matching the positional ? placeholders in the
 * query template.  Values are kept as Object so callers can send integers,
 * strings, or decimals – ReportService.coerceParams converts them to the
 * type declared in the query's ParamDef list.
 *
 * If params is omitted or shorter than the query's declaration, missing
 * values fall back to the defaultValue specified in queries.json.
 */
@Getter
@Setter
public class RunRequest {
    private List<Object> params;
}
