package edu.dbms.education.subject;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateSubjectRequest {
    @NotBlank          private String  code;
    @NotBlank          private String  title;
    @Min(1) @Max(6)    private int     credits;
    @NotNull           private Integer departmentId;
                       private Integer programId;
    @Min(1)            private int     semester;
                       private String  description;
}
