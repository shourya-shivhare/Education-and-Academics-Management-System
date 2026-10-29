package edu.dbms.education;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Education and Academics Management System backend.
 *
 * DBMS focus: all business rules live in MySQL (constraints, triggers,
 * stored procedures, views, roles). This layer is a thin orchestration
 * shell that issues parameterised SQL and surfaces the SQL trace.
 */
@SpringBootApplication
public class EducationManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EducationManagementApplication.class, args);
    }
}
