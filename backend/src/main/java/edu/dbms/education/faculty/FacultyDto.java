package edu.dbms.education.faculty;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/** Read-side projection combining person + faculty columns. */
@Getter
@AllArgsConstructor
public class FacultyDto {

    // person
    private final int       personId;
    private final String    firstName;
    private final String    lastName;
    private final String    email;

    // faculty
    private final String    employeeNo;
    private final int       departmentId;
    private final String    departmentName;
    private final String    designation;
    private final LocalDate hireDate;
    private final Integer   maxLoad;
}
