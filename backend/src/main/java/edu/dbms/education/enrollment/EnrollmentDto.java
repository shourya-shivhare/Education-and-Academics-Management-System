package edu.dbms.education.enrollment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class EnrollmentDto {
    private final int        enrollmentId;
    private final int        studentId;
    private final String     rollNo;
    private final String     studentName;
    private final int        offeringId;
    private final String     subjectCode;
    private final String     subjectTitle;
    private final String     termName;
    private final LocalDate  enrollDate;
    private final String     status;
    private final BigDecimal finalMarks;   // nullable until grades computed
    private final String     grade;        // nullable
    private final boolean    isDetained;
}
