package edu.dbms.education.enrollment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EnrollRequest {
    @NotNull private Integer studentId;
    @NotNull private Integer offeringId;
}
