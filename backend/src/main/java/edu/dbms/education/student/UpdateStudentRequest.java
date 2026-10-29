package edu.dbms.education.student;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Request body for PUT /api/students/{id}. Only mutable fields. */
@Getter
@Setter
public class UpdateStudentRequest {

    // person fields
    private String addressLine;
    private String city;
    private String state;
    private String pinCode;

    // student fields
    private String status;         // Active / Graduated / Dropped / Suspended
    @Min(1)
    private Integer currentSemester;
}
