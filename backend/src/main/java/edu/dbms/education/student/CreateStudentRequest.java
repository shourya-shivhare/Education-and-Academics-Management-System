package edu.dbms.education.student;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Request body for POST /api/students. */
@Getter
@Setter
public class CreateStudentRequest {

    // ── person fields ─────────────────────────────────────────────────────
    @NotBlank  private String    firstName;
    @NotBlank  private String    lastName;
    @Email     private String    email;
    @NotNull   private LocalDate dateOfBirth;
               private String    gender;       // M / F / Other – nullable
               private String    addressLine;
               private String    city;
               private String    state;
               private String    pinCode;

    // ── student fields ────────────────────────────────────────────────────
    @NotBlank  private String    rollNo;
    @NotNull   private Integer   programId;
    @NotNull   private LocalDate admissionDate;
    @Min(1)    private int       currentSemester = 1;
    // status defaults to 'Active' in the DB
}
