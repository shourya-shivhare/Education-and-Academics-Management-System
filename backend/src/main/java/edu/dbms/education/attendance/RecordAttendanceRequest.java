package edu.dbms.education.attendance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class RecordAttendanceRequest {
    @NotNull private Integer   enrollmentId;
    @NotNull private LocalDate classDate;
    @NotNull private String    status;  // Present / Absent / Late
}
