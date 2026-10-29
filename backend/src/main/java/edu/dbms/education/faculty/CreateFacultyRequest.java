package edu.dbms.education.faculty;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class CreateFacultyRequest {
    @NotBlank  private String    firstName;
    @NotBlank  private String    lastName;
    @Email     private String    email;
    @NotNull   private LocalDate dateOfBirth;
               private String    gender;

    @NotBlank  private String    employeeNo;
    @NotNull   private Integer   departmentId;
               private String    designation;
    @NotNull   private LocalDate hireDate;
               private Integer   maxLoad;
}
