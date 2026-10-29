package edu.dbms.education.marks;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * marks PK is (enrollment_id, offering_id, assessment_no) – composite.
 * There is no single marks_id surrogate key.
 */
@Getter
@AllArgsConstructor
public class MarksDto {
    private final int        enrollmentId;
    private final int        offeringId;
    private final int        assessmentNo;
    private final String     assessmentTitle;
    private final int        maxMarks;
    private final int        weightage;
    private final int        studentId;
    private final String     rollNo;
    private final BigDecimal marksObtained;
    private final int        version;          // optimistic lock version
}
