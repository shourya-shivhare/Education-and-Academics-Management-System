package edu.dbms.education.student;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Read-side projection combining person + student columns.
 *
 * DBMS concept: IS-A relationship (total participation).
 * student.student_id = person.person_id.  The JOIN is always inner because
 * every student row must have a corresponding person row (FK NOT NULL).
 */
@Getter
@AllArgsConstructor
public class StudentDto {

    // person columns
    private final int       personId;
    private final String    firstName;
    private final String    lastName;
    private final String    email;
    private final LocalDate dateOfBirth;
    private final String    gender;

    // student columns
    private final String    rollNo;
    private final int       programId;
    private final String    programName;
    private final LocalDate admissionDate;
    private final String    status;
    private final int       currentSemester;
}
