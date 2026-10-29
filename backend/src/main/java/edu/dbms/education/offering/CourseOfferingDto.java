package edu.dbms.education.offering;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CourseOfferingDto {
    private final int    offeringId;
    private final int    subjectId;
    private final String subjectCode;
    private final String subjectTitle;
    private final int    facultyId;
    private final String facultyName;
    private final int    termId;
    private final String termName;
    private final String section;
    private final int    capacity;
    private final int    enrolledCount;   // derived: COUNT active enrollments
    private final String status;
}
