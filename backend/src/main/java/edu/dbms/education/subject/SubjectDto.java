package edu.dbms.education.subject;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SubjectDto {
    private final int     subjectId;
    private final String  code;
    private final String  title;
    private final int     credits;
    private final int     departmentId;
    private final String  departmentName;
    private final Integer programId;
    private final int     semester;
    private final String  description;
}
