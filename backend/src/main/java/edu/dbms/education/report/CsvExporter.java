package edu.dbms.education.report;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts a list of column-map rows into RFC 4180-compliant CSV text.
 *
 * Design:
 * - Header row: column names in result-set order (preserved by ColumnMapRowMapper's
 *   LinkedHashMap).
 * - Data rows: each value converted to String; null → empty cell.
 * - Values that contain a comma, double-quote, or newline are enclosed in
 *   double-quotes with internal double-quotes doubled ("").
 *
 * Returned as a plain String so callers can write it to HttpServletResponse
 * without an intermediate byte array allocation.
 */
@Component
public class CsvExporter {

    private static final char   SEPARATOR = ',';
    private static final char   QUOTE     = '"';
    private static final String CRLF      = "\r\n";

    /**
     * Converts rows (list of column→value maps) to a CSV string.
     *
     * @param rows result rows from ReportRunner – each row is a LinkedHashMap
     *             with column names as keys in result-set order
     * @return     CSV text including header row; empty string if rows is empty
     */
    public String toCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(rows.size() * 80);

        // Header row – column names from the first row's key set
        boolean first = true;
        for (String col : rows.get(0).keySet()) {
            if (!first) sb.append(SEPARATOR);
            sb.append(escape(col));
            first = false;
        }
        sb.append(CRLF);

        // Data rows
        for (Map<String, Object> row : rows) {
            boolean firstCol = true;
            for (Object value : row.values()) {
                if (!firstCol) sb.append(SEPARATOR);
                sb.append(value == null ? "" : escape(value.toString()));
                firstCol = false;
            }
            sb.append(CRLF);
        }

        return sb.toString();
    }

    /**
     * RFC 4180 field quoting:
     * Wrap in double-quotes if the value contains a comma, double-quote,
     * carriage return, or newline.  Internal double-quotes are doubled.
     */
    private String escape(String value) {
        if (value == null) return "";
        boolean needsQuote = value.indexOf(SEPARATOR) >= 0
                || value.indexOf(QUOTE) >= 0
                || value.indexOf('\n')  >= 0
                || value.indexOf('\r')  >= 0;
        if (!needsQuote) return value;
        return QUOTE + value.replace("\"", "\"\"") + QUOTE;
    }
}
