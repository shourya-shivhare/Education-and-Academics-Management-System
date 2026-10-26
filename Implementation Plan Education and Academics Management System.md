# Education and Academics Management System: Sequential Implementation Plan

A series of ready-to-paste prompts, in build order, for implementing the project described in the requirements document. Each prompt depends only on the ones before it. **All verification is manual.** No unit tests, integration tests or test frameworks are written anywhere (no JUnit, Mockito, Jest, pytest). Each prompt ends with a manual check you perform yourself.

## How to use this plan

1. Start every new AI session by pasting **Prompt 0** (the shared rules), then paste the prompt you are working on.
2. Do the prompts strictly in order. Do not start a phase until the manual check of the previous one passes.
3. After each prompt, commit to git with the prompt number in the message, so you can roll back.
4. Keep screenshots of each manual check. They go straight into the final report.

| Phase | Prompts | Outcome |
| --- | --- | --- |
| 0. Foundation | 0–1 | Shared rules, repo and tooling |
| 1. Database design | 2–3 | ER/EER, keys, FDs, normalization |
| 2. Database build | 4–10 | Schema, data, queries, views, routines, roles, demo scripts |
| 3. Backend | 11–15 | Spring Boot API with SQL trace, JWT, RBAC, labs |
| 4. Frontend | 16–18 | React app with CRUD, reports, schema page, labs |
| 5. AI service | 19–22 | Training, FastAPI, Spring and React integration |
| 6. Finish | 23 | Full manual test pass, report, viva notes |

---

## Phase 0: Foundation

### Prompt 0: Shared rules (paste at the start of every session)

```
Project: Education and Academics Management System (college DBMS coursework).
Stack: MySQL 8 (InnoDB) as the database, Spring Boot 3 (Java 17+) backend using JdbcTemplate with hand-written SQL (NO JPA/Hibernate), Flyway for schema migrations (flyway-core and flyway-mysql), React (Vite) frontend, Python FastAPI service for ML.

Rules you must follow:
1. The DBMS is the primary focus. Business rules live in MySQL (constraints, triggers, procedures, views, roles). Java only calls them.
2. DO NOT write any tests of any kind: no unit tests, no integration tests, no test dependencies, no test folders. Testing is manual only. Be concise and direct in your output to conserve tokens.
3. Use parameterized queries only in application code. No string-concatenated SQL anywhere except one clearly marked demo file for the SQL injection demonstration.
4. Flyway is the only thing that changes the schema. Hibernate ddl-auto is not used.
5. Spring Boot is the only service the browser talks to. The Python service is internal and requires an X-Internal-Key header.
6. Every API response uses the envelope: { data, trace: [ {sql, params, rowsAffected, timeMs} ] }.
7. **DO NOT perform any automated git commits** or generate commit automation scripts. All Git operations and commit messages must be left entirely for me to handle manually.
8. Write complete, runnable code with short comments explaining the DBMS concept being shown. Tell me exactly which files you created or changed and how to run and manually verify them.
9. If something in my request conflicts with these rules, tell me before coding.
```

### Prompt 1: Repository and tooling

**Depends on:** Prompt 0.

```
Create the monorepo structure: backend/ (Spring Boot), frontend/ (React Vite), ai-service/ (FastAPI), database/ (seed/, queries/, demo/), docs/. Create a root README with prerequisites (JDK 17+, Maven, Node 18+, Python 3.10+, MySQL 8) and run instructions for each part. Provide a .gitignore. Provide a SQL script database/setup/00_create_database_and_users.sql that creates the database edu_db with utf8mb4, a migration user (flyway_user, full privileges on edu_db including CREATE ROLE and GRANT OPTION as needed) and a runtime user (app_user, no DDL rights; grants will be added by migrations). Do not create any test folders.
```

**Manual check:**

- Run the setup script in MySQL and log in as both users.
- Confirm `flyway_user` can create a table and `app_user` cannot.
- Confirm every folder exists and the README steps make sense.

---

## Phase 1: Database design (documents first)

### Prompt 2: ER/EER model and relational mapping

**Depends on:** Prompt 1.

```
Produce docs/01_er_eer_design.md for the education system. Include: (a) the full entity list with attributes (Department, Program, Person, Person_Phone, Student, Faculty, Subject, Subject_Prerequisite, Term, Classroom, Course_Offering, Timetable_Slot, Enrollment, Attendance, Assessment, Marks, Grade_Scale, Fee_Payment, Users, Audit_Log, Student_Risk); (b) all relationships with cardinalities, including the ternary Course_Offering (Subject x Faculty x Term), the recursive prerequisite relationship, the Faculty-Department HOD circular relationship, and Enrollment resolving Student M:N Offering; (c) EER features: Person specialization into Student and Faculty, multivalued phone attribute, derived attributes (age, attendance percent, CGPA), weak-entity style Assessment; (d) the 8 business rules (26-credit limit, prerequisites passed, capacity, no faculty or room double-booking, 75 percent attendance detention, 40 percent pass mark with grade scale, marks not above max, assessment weightage not above 100); (e) the step-by-step ER-to-relational mapping. Also provide the ER diagram as dbdiagram.io DBML code and as a Mermaid erDiagram, so I can render it.
```

**Manual check:**

- Paste the DBML into dbdiagram.io and confirm it renders without errors.
- Check every business rule is listed and every table appears in the diagram.
- Export the diagram as an image into `docs/er_diagram.png` (the frontend will show this later).

### Prompt 3: Keys, functional dependencies, normalization

**Depends on:** Prompt 2.

```
Produce docs/02_keys_fd_normalization.md. For every relation give the primary key, all candidate keys and the foreign keys. Give the functional dependency set per relation. Then show a worked normalization from one flat table (student_id, student_name, program, dept, dept_head, subject_code, subject_name, credits, faculty, term, assessment, marks) through 1NF, 2NF and 3NF, naming each violation removed. Add a BCNF example Teaching(student, subject, faculty) with {student, subject} -> faculty and faculty -> subject: show it is in 3NF but not BCNF, decompose it, prove the decomposition is lossless and state that it does not preserve one FD. Add a note on the deliberate denormalization of final_marks and grade in Enrollment.
```

**Manual check:**

- Pick three tables and verify the keys against the ER design.
- Verify the BCNF example by finding the candidate keys yourself.
- Make sure the final tables here match the table list in Prompt 2.

---

## Phase 2: Database build

### Prompt 4: Core schema migrations (DDL)

**Depends on:** Prompts 2–3.

```
Write Flyway migrations in backend/src/main/resources/db/migration/: V1__core_tables.sql (Department, Program, Person, Person_Phone, Student, Faculty, Subject, Subject_Prerequisite, Term, Classroom, Grade_Scale), V2__academic_tables.sql (Course_Offering, Timetable_Slot, Enrollment, Attendance, Assessment, Marks with a version column, Fee_Payment, Users, Audit_Log, Student_Risk) and V3__alter_circular_fk.sql (add Department.hod_faculty_id foreign key with ALTER, and one modify-column example). Use PRIMARY KEY, FOREIGN KEY with explicit ON DELETE and ON UPDATE (RESTRICT for academic records, CASCADE for phones and attendance), UNIQUE, NOT NULL, CHECK (credits 1-6, capacity > 0, marks >= 0, weightage 0-100, end_time > start_time, subject_id <> prereq_subject_id), DEFAULT, AUTO_INCREMENT and ENUM. Engine InnoDB. Do not store derived values (age, attendance percent, CGPA); only final_marks and grade in Enrollment. Name every constraint explicitly.
```

**Manual check:**

- Start MySQL, run Flyway (via the Spring app or Flyway CLI) and confirm all migrations apply.
- Run `SHOW TABLES`, `SHOW CREATE TABLE Enrollment` and inspect `flyway_schema_history`.
- Try three bad inserts by hand (negative credits, duplicate enrollment, orphan foreign key) and confirm each is rejected.

### Prompt 5: Indexes and reference data

**Depends on:** Prompt 4.

```
Write V4__indexes.sql and V5__reference_data.sql. Indexes: composite (offering_id, class_date) on Attendance, (student_id, term_id) on Fee_Payment, (subject_id, term_id) on Course_Offering, and a FULLTEXT index on Subject(name, description). Do not duplicate indexes InnoDB already creates for foreign keys; add a comment explaining that. Reference data: 6 departments, the programs, 6 terms, the Grade_Scale table (O=10, A+=9, A=8, B+=7, B=6, C=5, P=4, F=0 with percentage ranges and the 40 percent pass mark), and a set of classrooms.
```

**Manual check:**

- `SHOW INDEX FROM Attendance` and the other tables to see the new indexes.
- `SELECT * FROM Grade_Scale` and confirm the ranges cover 0 to 100 with no gaps or overlaps.

### Prompt 6: Seed data generator

**Depends on:** Prompt 5.

```
Write database/seed/generate_data.py (Python, Faker with Indian locale, fixed random seed, mysql-connector or SQLAlchemy). It must run after the migrations and populate: about 30 faculty, 40 subjects with descriptions (needed later for TF-IDF), subject prerequisites, about 300 students over 12 programs and several admission years, course offerings across 6 terms, about 1800 enrollments (respecting the 26-credit limit, capacity and prerequisites), about 70000 attendance rows, assessments (internal 1, internal 2, assignment, final) with weightage totalling 100, about 9000 marks rows, fee payments, and Users rows with BCrypt-hashed passwords for one admin, one registrar, several faculty and several students. Build realistic correlations: lower attendance and later fee payment lead to lower marks. Add noise and surprise cases (good attendance but failing; poor attendance but passing). Insert in batches. Print a summary of row counts. The script must be re-runnable (truncate and reload with foreign key checks handled safely). NOTE: triggers do not exist yet, so the script itself must respect the business rules.
```

**Manual check:**

- Run the script and compare printed counts to the targets.
- Run `SELECT COUNT(*)` on each table and spot-check 5 students for sensible data.
- Compute average marks for students with attendance below 75 and above 90; confirm the gap is visible.
- Run the script twice and confirm it reloads cleanly.

### Prompt 7: The 22 labeled queries

**Depends on:** Prompt 6.

```
Write database/queries/ with one file per query (q01 to q22), each with a header comment naming the concept and a one-line business question. Cover: 1 five-table INNER JOIN transcript, 2 LEFT JOIN subjects with offering counts including zero, 3 RIGHT JOIN offerings with students, 4 FULL OUTER via LEFT UNION RIGHT, 5 SELF JOIN subject with prerequisite, 6 IN subquery, 7 EXISTS and NOT EXISTS, 8 ANY and ALL, 9 correlated subquery (above own subject average), 10 subquery in FROM, 11 aggregates (COUNT, SUM, AVG, MIN, MAX), 12 GROUP BY, 13 HAVING pass rate below 60 percent, 14 HAVING with join (faculty with more than 3 offerings), 15 RANK and DENSE_RANK window functions, 16 LAG running average, 17 CTE term-wise CGPA, 18 recursive CTE prerequisite chain, 19 set operations using join equivalents (no INTERSECT or EXCEPT so it works on any MySQL 8.x), 20 CASE grade bands, 21 date functions attendance by month, 22 ROLLUP. Use parameter placeholders where a value is needed. Also write database/queries/queries.json listing id, title, concept, sql and parameter names so the backend Reports page can load them.
```

**Manual check:**

- Run each query in MySQL Workbench or the CLI and confirm none errors or returns empty without reason.
- Verify three results by hand with a small subquery (for example one student's CGPA).
- Save a screenshot of the results of each concept for the report.

### Prompt 8: Views and functions

**Depends on:** Prompt 7.

```
Write repeatable migrations R__functions.sql and R__views.sql. Functions: fn_grade_point(marks), fn_attendance_pct(enrollment_id), fn_cgpa(student_id). Views: v_student_transcript, v_attendance_summary (held, attended, percent, shortage flag), v_faculty_workload, v_dept_performance, v_student_public (a security view that hides date of birth, address and fee data), and v_updatable_demo (an updatable view with WITH CHECK OPTION). Also create the v_ml_features view later in Prompt 19, not here. Add comments on why aggregate views are not updatable.
```

**Manual check:**

- `SELECT * FROM` each view and compare a student’s transcript with the raw tables.
- Check `fn_cgpa` for one student against a manual calculation.
- Try an INSERT through the updatable view that violates the check option and confirm it fails. Try an UPDATE on an aggregate view and confirm the error.

### Prompt 9: Procedures and triggers

**Depends on:** Prompt 8.

```
Write repeatable migrations R__procedures.sql and R__triggers.sql (use DELIMITER, which the Flyway MySQL parser supports). Triggers: BEFORE INSERT on Enrollment (capacity, 26-credit limit, prerequisites completed with passing grade), BEFORE INSERT on Timetable_Slot (faculty and room clash), BEFORE INSERT/UPDATE on Marks (marks not above assessment max), BEFORE INSERT/UPDATE on Assessment (total weightage at most 100), AFTER UPDATE on Marks (write old and new values to Audit_Log), AFTER INSERT on Attendance (recompute percentage and set Enrollment.is_detained below 75), BEFORE DELETE on Student (block if academic history exists, log the attempt). Use SIGNAL SQLSTATE 45000 with clear messages. Procedures: sp_enroll_student(student, offering) with an exit handler that rolls back and resignals, START TRANSACTION and SELECT ... FOR UPDATE on the Course_Offering row before the INSERT; sp_drop_enrollment; sp_compute_final_grades(offering) using a cursor and the Grade_Scale lookup; sp_generate_transcript(student); sp_term_closure(term). Note that a trigger cannot modify its own table, so detention is stored in Enrollment.
```

**Manual check:**

- Trigger by trigger: attempt each violating action by hand and confirm the exact error message.
- Update a marks value and check a new Audit\_Log row with old and new values.
- Insert absent attendance rows until a student drops below 75 percent and confirm `is_detained` flips.
- Call each procedure and verify the results.

### Prompt 10: Roles, security and demo scripts

**Depends on:** Prompt 9.

```
Write V6__roles_and_grants.sql: CREATE ROLE admin_role, registrar_role, faculty_role, student_role, ml_readonly_role with GRANT statements (registrar: SELECT/INSERT/UPDATE on Student, Enrollment, Fee_Payment plus EXECUTE on enrollment procedures, no access to Users; faculty: SELECT on teaching views plus INSERT/UPDATE on Attendance and Marks only; student: SELECT on v_student_public and v_student_transcript only; ml_readonly: SELECT on the ML view and subject catalog view only), a column-level grant example, and grants for app_user to run everything the backend needs. Then write database/demo/ scripts, each with comments and expected output: 01_acid_atomicity.sql (fee payment plus enrollment with forced failure, plus SAVEPOINT demo), 02_acid_consistency.sql, 03_isolation_levels.sql (two-session instructions for each level and what is observed in MySQL), 04_durability_notes.md, 05_deadlock.sql (two-session opposite-order updates, error 1213), 06_last_seat_race.sql (naive vs FOR UPDATE), 07_explain_index_proof.sql (IGNORE INDEX vs index), 08_rbac_demo.sql (create DB users, show GRANT, REVOKE), 09_ddl_alter_drop_truncate.sql, 10_sql_injection_demo.md (one marked vulnerable query vs parameterized, written as documentation not as runtime code).
```

**Manual check:**

- Log in as each DB user and confirm allowed actions work and forbidden ones fail.
- Run the two-terminal demos side by side and capture screenshots of every outcome, including the deadlock victim error.
- Run the EXPLAIN demo and note rows examined before and after the index.

---

## Phase 3: Backend (Spring Boot)

### Prompt 11: Spring Boot skeleton, Flyway, SQL Trace

**Depends on:** Prompt 10.

```
Create the Spring Boot 3 project in backend/ with Spring Web, Spring JDBC, Spring Security, MySQL driver, Flyway (flyway-core and flyway-mysql), Validation and Lombok (optional). No test dependencies. application.yml: two datasources, Flyway using flyway_user and the app using app_user. Implement SqlTraceService with a request-scoped trace list, and a JdbcTemplate wrapper or datasource proxy that records SQL text, parameters, rows affected and time in ms for every statement, including CALL statements through SimpleJdbcCall. Implement a ResponseEnvelope and a ControllerAdvice that returns { data, trace } and translates MySQL SIGNAL errors (SQLSTATE 45000) into HTTP 409 with the message intact. Add CORS for the Vite dev server. Add a GET /api/health endpoint that runs SELECT 1 and returns its trace.
```

**Manual check:**

- Start the app and watch Flyway apply (or skip) migrations.
- Call `GET /api/health` in Postman and confirm the `trace` array shows the SQL and timing.

### Prompt 12: Authentication and RBAC

**Depends on:** Prompt 11.

```
Implement JWT authentication with Spring Security: POST /api/auth/login reads the Users table with JdbcTemplate (parameterized), verifies BCrypt, returns a token containing user id, role and person id. Roles: ADMIN, REGISTRAR, FACULTY, STUDENT. Add a JWT filter, method security with @PreAuthorize, and a helper that exposes the current person id so faculty queries can filter by faculty_id and student queries by student_id. Return 401 and 403 with the standard envelope. Include the login query in the trace.
```

**Manual check:**

- Log in as each role in Postman and decode the token.
- Call a protected endpoint with no token, a wrong-role token and a correct token, and confirm 401, 403 and 200.

### Prompt 13: CRUD APIs

**Depends on:** Prompt 12.

```
Implement controller, service (@Transactional) and repository layers (JdbcTemplate with explicit SQL, constructor injection) for Students, Faculty, Subjects, Course Offerings, Enrollments, Attendance and Marks. Each supports insert, search with filters (name, department, program, term, status), pagination, update and delete where meaningful. Enrollment goes through CALL sp_enroll_student and sp_drop_enrollment. Marks update uses optimistic locking: UPDATE ... SET marks_obtained = ?, version = version + 1 WHERE marks_id = ? AND version = ?, and a conflict response when 0 rows are affected. Role rules: ADMIN and REGISTRAR manage students and enrollments, FACULTY enters attendance and marks only for their own offerings, STUDENT reads only their own data. Keep every SQL statement visible in the repository code.
```

**Manual check:**

- In Postman, insert, search, update and delete a student, and confirm the trace shows the real SQL.
- Try an enrollment that breaks each rule (full class, missing prerequisite, over 26 credits) and confirm a 409 with the trigger message.
- As faculty, try to edit marks of another faculty’s offering and confirm 403.

### Prompt 14: Reports and schema endpoints

**Depends on:** Prompt 13.

```
Implement GET /api/reports (list the 22 queries from queries.json) and POST /api/reports/{id}/run with parameters (parameterized execution only). Add dedicated report endpoints: transcript, attendance shortage list, toppers, department performance. Add CSV export for any report. Add GET /api/schema returning tables, columns, keys and foreign keys from information_schema, and GET /api/schema/er-image serving docs/er_diagram.png. Add a read-only SELECT console endpoint that runs only a single SELECT statement, is restricted to ADMIN, and rejects anything else (optional feature).
```

**Manual check:**

- Run all 22 queries through the API and compare results with your direct SQL runs.
- Open the schema endpoint and compare it with `SHOW CREATE TABLE` output.
- If you built the console, confirm `DROP TABLE` and multi-statement input are rejected.

### Prompt 15: ACID Lab and Concurrency Lab backends

**Depends on:** Prompt 14.

```
Implement lab endpoints under /api/labs, ADMIN only. ACID: atomicity (fee payment plus enrollment in one @Transactional with an optional forced failure, then query to prove rollback), consistency (attempt invalid operations and return the exact DB errors), isolation (run a scenario under a chosen isolation level with @Transactional(isolation=...) and two threads, return the observed reads), durability (explanatory response plus a commit that the user can verify after restart). Concurrency: last-seat race (two threads enrolling into a one-seat offering, in naive mode without locking and in safe mode through sp_enroll_student), lost update on marks (naive overwrite vs optimistic locking), deadlock (two threads updating two rows in opposite order, catch error 1213 and retry once). Every endpoint returns a step-by-step log plus the SQL trace. Use a dedicated lab offering and lab rows that are reset before each run so the demos are repeatable.
```

**Manual check:**

- Run each lab several times and confirm the same outcome each time.
- Confirm naive mode overbooks the seat and safe mode does not.
- Confirm the deadlock lab shows the victim error and the retry succeeding.

---

## Phase 4: Frontend (React)

### Prompt 16: React skeleton, login, trace panel

**Depends on:** Prompt 15.

```
Create the React (Vite) app in frontend/ with React Router, Axios, Bootstrap (or MUI) and Recharts. No test libraries. Implement: an Axios instance that adds the JWT and unwraps { data, trace }; a login page; protected routes; a role-based navbar (admin, registrar, faculty, student see different menus); a reusable TracePanel component that shows, under any action, the flow Front End -> SQL Query -> Database -> Result as a small diagram plus a table of SQL, params, rows affected and time; and a global store for the latest trace. Add the "View ER / Schema" button to the navbar now (page filled in Prompt 18).
```

**Manual check:**

- Log in as each role and confirm the menus differ.
- Trigger a request and confirm the TracePanel shows the SQL and timing.
- Refresh the page and confirm the session behaves sensibly.

### Prompt 17: CRUD and operations pages

**Depends on:** Prompt 16.

```
Build pages for Students, Faculty, Subjects, Course Offerings, Enrollment, Attendance entry and Marks entry. Each has a searchable, paginated table with filters, forms for insert and update with validation, and delete with confirmation. Show every action’s TracePanel directly below it. Show trigger errors (409) as clear alerts that quote the database message. Marks entry shows optimistic-lock conflicts with a prompt to reload. Keep the design simple and consistent.
```

**Manual check:**

- Perform insert, search, update and delete on each entity in the browser.
- Trigger each business-rule violation from the UI and read the message.
- Open two browser sessions, edit the same marks row in both, and confirm the conflict message in the second.

### Prompt 18: Reports, schema page, labs pages

**Depends on:** Prompt 17.

```
Build: a Reports page listing the 22 queries with a parameter form, a result table, CSV export and the SQL shown beside the result, plus chart views for department performance and toppers; a Schema page opened from the navbar button showing the ER image and a live table and column and foreign-key explorer from /api/schema; an ACID Lab page and a Concurrency Lab page with a button per scenario, a step-by-step log view and the TracePanel; and (if built) the admin SELECT console.
```

**Manual check:**

- Run all 22 reports from the UI and compare with Prompt 7 results.
- Open the Schema page and check the ER image and listing match the database.
- Run each lab from the UI and confirm it matches the Postman outcomes from Prompt 15.

---

## Phase 5: AI service

### Prompt 19: ML feature view, EDA, preprocessing

**Depends on:** Prompt 10 (data and roles), best done after Prompt 6.

```
Create repeatable migration R__ml_views.sql with v_ml_features (one row per student per term, mid-term snapshot only, to avoid label leakage): attendance_pct_midterm, internal1_avg_pct, internal2_avg_pct, assignment_submission_rate, prev_term_gpa, cgpa_so_far, backlog_count, fee_delay_days, credits_this_term, program_id, year_of_study, marks_trend, is_first_year, plus the label at_risk (final grade F in any subject or detained at term end) kept in a separate view or column clearly marked as label-only. Create ai-service/ with requirements.txt (fastapi, uvicorn, pandas, numpy, scikit-learn, imbalanced-learn, shap, sqlalchemy, pymysql, joblib, matplotlib, seaborn, python-dotenv, pydantic; no pytest). Write training/eda.ipynb: load from MySQL with the ml_readonly user, show class balance, distributions, correlation heatmap, at-risk rate by attendance band, and an IQR outlier report. Explain the leakage design in a markdown cell.
```

**Manual check:**

- Query `v_ml_features` and confirm one row per student per term and no final-marks columns among the features.
- Run the notebook end to end and confirm the plots make sense.
- Check the at-risk percentage is neither tiny nor close to 100.

### Prompt 20: Training and evaluation

**Depends on:** Prompt 19.

```
Write ai-service/training/train.py and evaluate.py. Build one scikit-learn Pipeline with ColumnTransformer (median imputation, IQR clipping, one-hot for program and year, StandardScaler). Time-based split: train on earlier terms, test on the latest term. Compare class_weight balanced against SMOTE inside CV folds only. Models: LogisticRegression, RandomForest, HistGradientBoostingClassifier with 5-fold StratifiedKFold and GridSearchCV. Select by recall on the at-risk class with a minimum precision floor. Tune the decision threshold from the precision-recall curve on a validation fold and calibrate with CalibratedClassifierCV. Evaluate on the held-out term: accuracy, precision, recall, F1, ROC-AUC, PR-AUC, confusion matrix, ROC and PR curves, a three-model comparison table, permutation importance, and a comparison against the rule attendance below 75 means at risk. Save artifacts/model.joblib, model_meta.json (version, date, features, threshold, metrics), metric images and metrics.json. Print an honest summary and note that the data is synthetic.
```

**Manual check:**

- Run `train.py` and inspect the printed metrics and saved images.
- Confirm the model beats the attendance-rule baseline, and that scores are not suspiciously perfect (if they are, revisit data noise or leakage).
- Confirm artifacts load in a fresh Python shell.

### Prompt 21: FastAPI service

**Depends on:** Prompt 20.

```
Build ai-service/app/ with FastAPI and Pydantic schemas: POST /predict, POST /predict-batch (probability, risk level LOW/MEDIUM/HIGH, top 3 contributing factors from SHAP as readable sentences, model version), GET /model/info, GET /model/metrics, POST /train (retrains from the database with the read-only user), GET /recommend/{student_id} (TF-IDF on subject name, description and keywords, student profile vector weighted by grade points, cosine similarity, excluding subjects with unmet prerequisites), GET /search?q= (TF-IDF cosine search), GET /health. Require the X-Internal-Key header on all routes except /health. Load the model once at startup. Return clear errors for malformed input. Use Swagger at /docs for manual testing. No test files.
```

**Manual check:**

- Open Swagger, call `/predict` with a low-attendance student and a high-attendance student, and confirm sensible probabilities and explanations.
- Call without the key and confirm rejection.
- Search for “learning from data” and confirm Machine Learning and Data Mining appear.

### Prompt 22: Spring and React AI integration

**Depends on:** Prompt 21 and Prompts 16–18.

```
Backend: implement RiskService with RestClient and timeouts. POST /api/risk/run?term=N runs SELECT * FROM v_ml_features WHERE term_id = ? (traced), sends the rows to /predict-batch, then writes results with INSERT ... ON DUPLICATE KEY UPDATE into Student_Risk (traced), including model_version and top_factors JSON. Add GET /api/risk (filters by offering, level, term; faculty see only their offerings; students see only their own), GET /api/risk/model-info and metrics (proxied), POST /api/risk/train (ADMIN only), recommendation and search proxy endpoints, and a graceful error when the AI service is down while the rest of the app keeps working. Frontend: an At-Risk Dashboard (sortable table, filters, Run Risk Analysis button, TracePanel), risk badges on the student profile and the faculty class list, a Model Insights page (metrics table, confusion matrix, ROC and feature importance charts), a what-if panel with sliders that call predict, a Recommendations tab, and a smart-search box on the Subjects page with a toggle comparing LIKE, FULLTEXT and AI search.
```

**Manual check:**

- Click Run Risk Analysis and confirm both SQL statements appear in the trace and `Student_Risk` fills with rows.
- Stop the Python service and confirm the app shows a friendly error and everything else still works.
- Log in as faculty and as a student and confirm each sees only the allowed risk data.
- Move the what-if sliders and confirm the probability changes sensibly.

---

## Phase 6: Finish

### Prompt 23: Full manual test pass, report and viva notes

**Depends on:** all previous prompts.

```
Produce (a) docs/manual_test_checklist.md: a table of every requirement from the project brief (ER/EER, keys and FDs, normalization, DDL and DML, joins and subqueries and aggregates, views and indexes, triggers and procedures, transactions and ACID, concurrency, security and RBAC, CRUD UI, SQL trace, ER/schema button, Front End -> SQL -> DB -> Result flow, AI preprocessing, model selection, training, testing, evaluation, AI integration with database and front end) with columns: how to demonstrate it, expected result, pass or fail, screenshot name; (b) docs/report_outline.md with a chapter list, where each screenshot goes, a written discussion of concurrency (locking, isolation levels, deadlocks), the security limitations (no native row-level security in MySQL, one runtime DB user plus JWT checks), the ML methodology, the leakage handling, the synthetic-data disclaimer and the results table; (c) docs/viva_notes.md with 40 likely viva questions and short model answers covering normalization, BCNF vs 3NF, triggers vs procedures, isolation levels, why FOR UPDATE, why optimistic locking, why JdbcTemplate, why Flyway, why a time-based split, why recall, and what label leakage is. Do not write any automated tests.
```

**Manual check (the final pass):**

- On a fresh machine or fresh database: run migrations, the seed script, the AI training, the AI service, the backend and the frontend, following only the README.
- Walk the checklist top to bottom as an examiner would, from the UI first, and mark every row.
- Rehearse a five-minute demo: login, enroll with a trigger error, SQL Trace, ER page, Concurrency Lab, risk dashboard.

---

## Troubleshooting rules for the whole plan

- **If a manual check fails,** paste the failing output back into the same prompt’s session and ask for a fix before moving on.
- **If a later prompt needs a change to an earlier migration,** add a new versioned migration. Never edit an applied V migration, except R\_\_ files, which are repeatable.
- **If the AI assistant adds tests, test dependencies or test folders,** tell it to remove them. They are out of scope.
- **If seed data and triggers conflict,** fix the generator, not the trigger, because the trigger is the business rule.
