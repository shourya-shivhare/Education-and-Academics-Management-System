package edu.dbms.education.offering;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateOfferingRequest {
    @NotNull          private Integer subjectId;
    @NotNull          private Integer facultyId;
    @NotNull          private Integer termId;
    @NotBlank         private String  section;
    @Min(1)           private int     capacity;
    // status defaults to 'Open'
}
