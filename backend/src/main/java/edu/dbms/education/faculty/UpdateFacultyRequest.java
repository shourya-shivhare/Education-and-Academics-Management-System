package edu.dbms.education.faculty;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateFacultyRequest {
    private String  designation;
    private Integer maxLoad;
    private Integer departmentId;
}
