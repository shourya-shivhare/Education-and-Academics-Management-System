package edu.dbms.education.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Descriptor of one named query loaded from queries.json.
 *
 * Uses Lombok @Getter/@Setter + @NoArgsConstructor so Jackson's default
 * ObjectMapper can deserialise it without custom configuration.
 *
 * sql is the raw template with ? positional placeholders – stored verbatim
 * from the JSON file and never string-interpolated at runtime.
 */
@Getter
@Setter
@NoArgsConstructor
public class QueryDefinition {

    private String          id;
    private String          title;
    private String          concept;
    private String          description;
    /** SQL template with ? placeholders – never modified or interpolated. */
    private String          sql;
    private List<ParamDef>  params;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ParamDef {
        private String name;
        private String type;            // "INTEGER" | "STRING" | "DECIMAL"
        @JsonProperty("default")
        private Object defaultValue;    // "default" is reserved in Java – mapped via @JsonProperty
        private String description;
    }
}
