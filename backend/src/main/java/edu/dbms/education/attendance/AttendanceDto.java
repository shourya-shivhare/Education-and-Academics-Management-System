package edu.dbms.education.attendance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AttendanceDto {
    private final int       attendanceId;
    private final int       enrollmentId;
    private final int       studentId;
    private final String    rollNo;
    private final LocalDate classDate;
    private final String    status;         // Present / Absent / Late
    private final String    subjectCode;
    private final String    offeringSection;
}
