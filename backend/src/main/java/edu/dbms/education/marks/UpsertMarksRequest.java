package edu.dbms.education.marks;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Used for both INSERT (first-time entry) and UPDATE (version must be supplied). */
@Getter @Setter
public class UpsertMarksRequest {
    @NotNull                     private Integer    enrollmentId;
    @NotNull                     private Integer    offeringId;
    @NotNull                     private Integer    assessmentNo;
    @NotNull @DecimalMin("0.0")  private BigDecimal marksObtained;
    /**
     * Required for UPDATE only.  On INSERT the DB initialises version = 1.
     * If null, the repository performs an INSERT (no version check needed).
     * If non-null, performs: UPDATE ... WHERE version = ? (optimistic lock).
     */
                                 private Integer    version;
}
