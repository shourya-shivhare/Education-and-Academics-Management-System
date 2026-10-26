# Education and Academics Management System: Project Requirements

College coursework project (DBMS). Stack: React, Spring Boot, Python, MySQL 8. This document lists every requirement from the project brief and how each one is implemented.

## 1. Project overview

**Goal:** build a database-centred Education and Academics Management System that manages departments, programs, students, faculty, subjects, course offerings, enrollment, attendance, marks, fees and users, and adds an AI feature that predicts at-risk students.

**Guiding principle:** the DBMS is the primary focus. Business rules live in MySQL (constraints, triggers, procedures, views, roles). Spring Boot only calls them, React only displays them, and the AI service learns from the data.

**Hard constraint from the student:** no unit testing of any kind (no JUnit, Mockito, Jest, pytest). Verification is done through demo scripts, the UI, the SQL Trace panel, Postman and Swagger.

## 2. Technology stack and architecture

| Layer | Technology | Responsibility |
| --- | --- | --- |
| Frontend | React (Vite), React Router, Axios, Bootstrap or MUI, Recharts | UI, SQL Trace panel, charts, ER/schema view, labs |
| Backend | Spring Boot 3, Spring Web, Spring Security with JWT, JdbcTemplate | REST APIs, transactions, auth, RBAC, SQL tracing |
| Migrations | Flyway (flyway-core and flyway-mysql) | Versioned schema, views, triggers, procedures, roles |
| Database | MySQL 8 (InnoDB) | All core logic and data |
| AI service | Python, FastAPI, pandas, scikit-learn, SHAP, SQLAlchemy, joblib | Training, prediction, explanations, recommendation, smart search |

```
React  →  Spring Boot  →  MySQL
              │
              └→  FastAPI (Python)  →  MySQL (read-only, training only)
```

**Key design decisions**

- **JdbcTemplate with hand-written SQL, not JPA/Hibernate.** Hibernate hides the SQL it generates, which weakens the DBMS focus and makes it hard to show the exact query. JdbcTemplate keeps every query visible, explainable in a viva and capturable by the trace panel.
- **Flyway alongside JdbcTemplate, not instead of it.** Flyway creates and versions the database. JdbcTemplate runs queries at runtime. They solve different problems and are used together.
- **Hibernate ddl-auto is off.** Flyway is the only thing that changes the schema.
- **Spring Boot is the only service the browser talks to.** The Python service is internal, protected by an X-Internal-Key header.
- **All writes to academic data go through Spring**, so the SQL Trace panel shows them and RBAC stays in one place.
- **Two DB users:** a privileged migration user for Flyway and a least-privilege runtime user for the app.
- **Database choice:** MySQL is assumed because the student did not name one. It supports triggers, procedures, roles, isolation levels and row locking.

## 3. Part A: DBMS core logic and SQL implementation

### A1. Entities, attributes, relationships, business rules, ER/EER model

**Requirement:** identify entities, attributes, relationships and business rules; develop the ER/EER model and convert it into a relational schema.

**Tables (about 20):** Department, Program, Person, Person\_Phone, Student, Faculty, Subject, Subject\_Prerequisite, Term, Classroom, Course\_Offering, Timetable\_Slot, Enrollment, Attendance, Assessment, Marks, Grade\_Scale, Fee\_Payment, Users, Audit\_Log, Student\_Risk.

**Relationships**

- Department 1:N Program, Student, Faculty, Subject.
- Faculty 1:1 Department as head of department (circular foreign key, added later with ALTER).
- Subject M:N Subject through Subject\_Prerequisite (recursive).
- Course\_Offering is a ternary relationship: Subject × Faculty × Term (plus classroom and section).
- Student M:N Course\_Offering, resolved by Enrollment, which carries its own attributes (status, grade, detained flag).
- Attendance and Marks depend on Enrollment. Assessment depends on Course\_Offering.

**EER features demonstrated**

- Specialization/generalization: Person → Student and Faculty.
- Multivalued attribute: phone numbers.
- Derived attributes: age, attendance percentage, CGPA (computed in views and functions, not stored).
- Weak-entity style dependency: Assessment under Course\_Offering.
- Recursive relationship: subject prerequisites.

**Business rules (each enforced inside the database)**

1. A student can take at most 26 credits per term.
2. A student cannot enroll unless prerequisites are completed with a passing grade.
3. Enrollment cannot exceed the offering’s capacity.
4. A faculty member cannot be double-booked in a time slot, and a room cannot be double-booked.
5. Attendance below 75% flags the student as detained.
6. The pass mark is 40%, and marks map to grades and grade points through Grade\_Scale.
7. Marks obtained cannot exceed an assessment’s maximum marks.
8. Total assessment weightage per offering cannot exceed 100%.

**Deliverables:** ER/EER diagram (dbdiagram.io or MySQL Workbench) and the written ER-to-relational mapping steps.

### A2. Keys, functional dependencies, normalization

**Requirement:** identify primary, candidate and foreign keys; identify functional dependencies; demonstrate normalization up to 3NF/BCNF where applicable.

- **Keys table:** for every relation, list the PK, candidate keys and FKs. Example: Student has candidate keys student\_id, roll\_no and email, with student\_id as PK.
- **FD sets per table.** Examples: `student_id → roll_no, program_id, admission_year`; `roll_no → student_id`; `{enrollment_id, assessment_id} → marks_obtained`.
- **Worked normalization** from one flat table (student, program, department, department head, subject, credits, faculty, term, assessment, marks):
  - 1NF: atomic values, no repeating groups.
  - 2NF: remove partial dependencies (student attributes depend only on student, subject attributes only on subject).
  - 3NF: remove transitive dependencies (student → program → department → head).
- **BCNF case:** `Teaching(student, subject, faculty)` with `{student, subject} → faculty` and `faculty → subject`. It is in 3NF but not BCNF. Decompose into `(faculty, subject)` and `(student, faculty)`. The decomposition is lossless but does not preserve the FD `{student, subject} → faculty`, and the write-up states this trade-off.
- **Denormalization note:** final\_marks and grade are stored in Enrollment on purpose, filled by a procedure, so transcripts do not recompute weighted marks each time.

### A3. DDL, DML, queries, sample data

**Requirement:** implement the database using DDL and DML; demonstrate joins, nested and correlated queries, subqueries, aggregate functions, GROUP BY, HAVING and similar; use sufficient and realistic sample data.

**DDL**

- CREATE with PRIMARY KEY, FOREIGN KEY (with ON DELETE and ON UPDATE actions), UNIQUE, NOT NULL, CHECK, DEFAULT, AUTO\_INCREMENT and ENUM.
- ALTER (add column, add the HOD foreign key, modify column), RENAME, DROP and TRUNCATE.
- Destructive commands live in `database/demo/*.sql` and are run manually. They are not Flyway migrations.
- FK policy: ON DELETE RESTRICT for academic records, ON DELETE CASCADE for dependents such as phones and attendance, ON UPDATE CASCADE generally.

**DML:** INSERT (single, multi-row, INSERT…SELECT, ON DUPLICATE KEY UPDATE), UPDATE (including with joins) and DELETE.

**Sample data (seeded Faker script, Indian locale, fixed random seed)**

- 6 departments, 12 programs, about 30 faculty, about 40 subjects, about 300 students, 6 terms.
- About 1,800 enrollments, about 70,000 attendance rows, about 9,000 marks rows.
- Built-in correlations (low attendance and late fees lead to lower marks) plus noise and surprise cases, so the queries are interesting and the ML is meaningful.
- Reference data (departments, grade scale, terms) goes into a Flyway migration. Bulk data comes from `generate_data.py`, run once after migrations.

**Query set (22 labeled queries, also exposed on the Reports page)**

| # | Concept | Query |
| --- | --- | --- |
| 1 | INNER JOIN (5 tables) | Full transcript: Student, Enrollment, Offering, Subject, Faculty |
| 2 | LEFT JOIN | All subjects with offering count, including zero |
| 3 | RIGHT JOIN | Offerings with enrolled students, including empty offerings |
| 4 | FULL OUTER (LEFT UNION RIGHT) | Departments with no programs vs. programs with no students |
| 5 | SELF JOIN | Subject with its prerequisite name |
| 6 | Subquery with IN | Students taught by a given faculty member |
| 7 | EXISTS / NOT EXISTS | Students with no fee dues; students who never enrolled |
| 8 | ANY / ALL | Students scoring above all peers in a subject |
| 9 | Correlated subquery | Students above the average of their own subject |
| 10 | Subquery in FROM | Top department by average of per-student averages |
| 11 | Aggregates | COUNT, SUM, AVG, MIN, MAX marks per subject |
| 12 | GROUP BY | Students per department and program |
| 13 | HAVING | Subjects with pass rate below 60% |
| 14 | HAVING with join | Faculty teaching more than 3 offerings in a term |
| 15 | Window function | RANK and DENSE\_RANK of students within a department |
| 16 | Window function | Running average per student across terms (LAG) |
| 17 | CTE | Term-wise CGPA |
| 18 | Recursive CTE | Full prerequisite chain for a subject |
| 19 | Set operations | Enrolled in A but not B; intersection through joins |
| 20 | CASE | Grade bands and attendance categories |
| 21 | Date functions | Attendance by month |
| 22 | ROLLUP | Enrollment by department and program with subtotals |

MySQL note: INTERSECT and EXCEPT exist only from 8.0.31, so join-based equivalents are used to work on any 8.x version.

### A4. Views, indexes, triggers, procedures, transactions, concurrency, security

**Views**

- `v_student_transcript`: joined transcript with grade points.
- `v_attendance_summary`: classes held, attended, percentage and shortage flag per enrollment.
- `v_faculty_workload`: offerings, student count and credit hours per faculty member.
- `v_dept_performance`: average marks, pass percentage and enrollment per department.
- `v_student_public`: security view hiding date of birth, address and fee data.
- `v_ml_features`: one row per student per term for the AI service.
- An updatable view with `WITH CHECK OPTION`, and a note on why aggregate views are not updatable.

**Indexes**

- InnoDB already indexes FK columns, so those are not duplicated.
- Added: composite `(offering_id, class_date)` on Attendance, `(student_id, term_id)` on Fee\_Payment, `(subject_id, term_id)` on Course\_Offering, and a FULLTEXT index on Subject name and description.
- Proof: run EXPLAIN on the attendance-shortage query with and without the index (plan type ALL vs. ref, rows examined, timing).

**Triggers** (errors use `SIGNAL SQLSTATE '45000'` with clear messages shown in the UI)

- BEFORE INSERT on Enrollment: capacity, 26-credit limit, prerequisites.
- BEFORE INSERT on Timetable\_Slot: faculty and room clashes.
- BEFORE INSERT/UPDATE on Marks: marks do not exceed maximum.
- BEFORE INSERT/UPDATE on Assessment: total weightage at most 100.
- AFTER UPDATE on Marks: write old and new values to Audit\_Log.
- AFTER INSERT on Attendance: recompute percentage and set `Enrollment.is_detained` below 75%. (MySQL does not allow a trigger to modify its own table, so the flag lives in Enrollment.)
- BEFORE DELETE on Student: block deletion when academic history exists and log the attempt.

**Stored procedures and functions**

- `sp_enroll_student(student, offering)`: transactional and lock-protected.
- `sp_drop_enrollment(student, offering)`.
- `sp_compute_final_grades(offering)`: weighted marks, cursor, Grade\_Scale lookup.
- `sp_generate_transcript(student)`: returns a result set.
- `sp_term_closure(term)`: batch-completes enrollments.
- `fn_cgpa(student)`, `fn_attendance_pct(enrollment)`, `fn_grade_point(marks)`.

**Transactions and ACID (an ACID Lab page runs each demo)**

- **Atomicity:** fee payment and enrollment in one transaction, a forced failure on step two, and proof that the payment rolled back. Also a SAVEPOINT and ROLLBACK TO demo.
- **Consistency:** invalid marks, duplicate enrollment and orphan FK inserts are rejected, and the exact error is shown.
- **Isolation:** two sessions run under each isolation level.
- **Durability:** commit, restart MySQL, show the row survived, and explain the InnoDB redo log and `innodb_flush_log_at_trx_commit`.

**Concurrency (a Concurrency Lab page runs two backend threads)**

| Scenario | Problem shown | Fix |
| --- | --- | --- |
| Last seat | Two students both succeed, overbooking | `SELECT … FOR UPDATE` on the offering row inside `sp_enroll_student` |
| Two faculty edit the same marks row | Lost update | Optimistic locking with a `version` column (`UPDATE … WHERE version = ?`, check rows affected) |
| Opposite-order updates on two rows | Deadlock | InnoDB aborts one transaction (error 1213) and Spring retries |
| Isolation levels | Dirty, non-repeatable and phantom reads | Comparison table backed by live two-session runs |

Accuracy note: MySQL’s default is REPEATABLE READ and InnoDB uses next-key locks, so phantoms are largely prevented. Dirty reads appear only under READ UNCOMMITTED. The report shows real behavior, not just the textbook table, and includes a written discussion of locking, isolation levels and deadlocks.

**Security and role-based access control**

| DB role | Access |
| --- | --- |
| admin\_role | All privileges on the schema |
| registrar\_role | SELECT, INSERT, UPDATE on Student, Enrollment, Fee\_Payment; EXECUTE on enrollment procedures; no access to Users |
| faculty\_role | SELECT on teaching views; INSERT and UPDATE on Attendance and Marks only |
| student\_role | SELECT on v\_student\_public and v\_student\_transcript only |
| ml\_readonly\_role | SELECT on v\_ml\_features and the subject catalog view only |

- CREATE ROLE, GRANT, REVOKE, plus column-level grants, demonstrated with separate DB users and scripts.
- App level: Spring Security with JWT, BCrypt password hashing, `@PreAuthorize` role checks, and a `WHERE faculty_id = ?` taken from the JWT for faculty data.
- Audit\_Log records who changed marks and when.
- SQL injection demo: a clearly marked, isolated vulnerable string-concatenated query (`' OR '1'='1`) against the parameterized version. All real code uses parameterized queries only.
- Limitation stated openly: MySQL has no native row-level security, so per-user filtering uses mapping tables, views and Spring checks. The Spring app connects with one least-privilege user.

## 4. Part B: Front-end design and database interaction

**Requirement:** a simple web front end connected to the database with insert, search, update, delete and report generation; illustrate the SQL executed and its results; a button or link to view the ER model/schema; show the flow Front End → SQL Query → Database → Result; keep the DBMS as the main focus.

**Backend structure:** controllers → services (`@Transactional`) → repositories (JdbcTemplate with explicit SQL). Procedure calls use `SimpleJdbcCall`.

**SQL Trace mechanism:** a `SqlTraceService` wraps JdbcTemplate (or a datasource proxy) and records each statement for the current request. It must cover plain queries, procedure calls (`CALL sp_enroll_student`), the ML feature query and the Student\_Risk writes. Every API response uses this envelope:

```
{ "data": {...}, "trace": [ { "sql": "...", "params": [...], "rowsAffected": 1, "timeMs": 4 } ] }
```

**React pages**

| Page | Features |
| --- | --- |
| Login | JWT login and role-based menus |
| Students, Faculty, Subjects | Insert, search with filters, update, delete |
| Enrollment | Enroll and drop, with trigger errors shown cleanly |
| Attendance and Marks | Faculty entry per offering |
| Reports | Transcript, shortage list, toppers, department performance with charts, CSV/PDF export, and all 22 labeled queries |
| SQL Trace panel | Under every action, drawn as a small flow: Front End → SQL Query → Database → Result |
| ER / Schema button | Navbar button showing the ER image and a live table, column and FK listing from `information_schema` |
| ACID Lab and Concurrency Lab | Run the demos from A4 (kept minimal) |
| At-Risk Dashboard and Model Insights | AI output (Part C) |

Optional: a read-only admin SQL console on a SELECT-only DB user, to strengthen the SQL illustration requirement.

## 5. Part C: AI/ML integration

**Requirement:** implement at least one meaningful AI/ML task on project data, with preprocessing, model selection, training, testing and performance evaluation, integrated with the database and front end as a useful intelligent feature.

**Primary task: at-risk student prediction (binary classification).** The model predicts whether a student will end the term at risk (final grade F in any subject, or detained).

**Avoiding label leakage:** features come only from a mid-term snapshot (about week 8): attendance so far, first internals, earlier results. The label is the end-of-term outcome. The report explains this explicitly.

**Features from `v_ml_features`:** attendance\_pct\_midterm, internal1\_avg\_pct, internal2\_avg\_pct, assignment\_submission\_rate, prev\_term\_gpa, cgpa\_so\_far, backlog\_count, fee\_delay\_days, credits\_this\_term, program\_id, year\_of\_study, marks\_trend, is\_first\_year.

**Preprocessing (one scikit-learn Pipeline with ColumnTransformer)**

- Median imputation for numeric values (with an is\_first\_year flag), most-frequent for categorical.
- IQR outlier check reported in the EDA, values clipped and not dropped.
- One-hot encoding for program and year, StandardScaler for numeric features.
- Time-based split: train on earlier terms, test on the latest term.
- Class imbalance: compare `class_weight='balanced'` with SMOTE applied only inside CV folds.
- EDA outputs: class balance, distributions, correlation heatmap, at-risk rate by attendance band.

**Model selection and training**

- Logistic Regression (baseline), Random Forest, and HistGradientBoostingClassifier.
- 5-fold stratified cross-validation with GridSearchCV over a small grid.
- Selection metric: recall on the at-risk class with a minimum precision floor, because missing a struggling student is the costly error.
- Threshold tuned from the precision-recall curve, probabilities calibrated with CalibratedClassifierCV.
- Saved as `model.joblib` and `model_meta.json` (version, date, features, threshold, metrics).

**Evaluation on the held-out latest term:** accuracy, precision, recall, F1, ROC-AUC, PR-AUC, confusion matrix, ROC and PR curves, a three-model comparison table, permutation feature importance, and a comparison against the simple rule “attendance below 75% means at risk”.

**Explainability:** SHAP gives the top 3 contributing factors per prediction, shown in the UI as readable sentences.

**Honesty note:** the data is synthetic with controlled correlations. The report says so plainly and presents the project as a demonstration of the pipeline, not a real-world accuracy claim. The generator adds noise and surprise cases to avoid a suspiciously perfect model.

**FastAPI endpoints (validated with Pydantic, demoed through Swagger, protected by X-Internal-Key)**

| Endpoint | Purpose |
| --- | --- |
| POST /predict | One student: probability, risk level, top factors |
| POST /predict-batch | Whole-class run |
| GET /model/info | Version, date, metrics, threshold |
| GET /model/metrics | Confusion matrix, ROC data, feature importance |
| POST /train | Retrain from the database (admin only, via Spring) |
| GET /recommend/{student\_id} | Elective recommendations |
| GET /search?q= | Intelligent course search |
| GET /health | Used by Spring to show AI service status |

**Integration flow**

1. React “Run Risk Analysis” calls Spring `POST /api/risk/run?term=N`.
2. Spring runs `SELECT * FROM v_ml_features WHERE term_id = ?` (shown in the SQL Trace).
3. Spring sends the features to FastAPI `/predict-batch`.
4. FastAPI returns probabilities and top factors.
5. Spring writes them with `INSERT … ON DUPLICATE KEY UPDATE` into Student\_Risk (shown in the SQL Trace).
6. The At-Risk Dashboard refreshes.

**Student\_Risk table:** student\_id, term\_id, risk\_probability, risk\_level (LOW/MEDIUM/HIGH), top\_factors (JSON), model\_version, predicted\_at.

**Database access rule:** Python reads the database directly only for training and for the recommendation catalog, using the read-only user. Prediction uses features sent by Spring. If the student prefers that Python never touches the database, Spring can export training data to `POST /train` instead.

**Resilience and roles:** Spring calls FastAPI with timeouts, and if the AI service is down the API returns a clear error while the rest of the app keeps working. Faculty see risk only for their own offerings, students see only their own badge, and only admin can retrain.

**Secondary feature: recommendation and smart search.** TF-IDF on subject name, description and keywords. A student profile vector is weighted by grade points, cosine similarity ranks unenrolled electives, and subjects with unmet prerequisites are filtered out using the same logic as the enrollment trigger. Smart search is compared in the report against LIKE and MySQL FULLTEXT. TF-IDF is chosen over deep learning because the catalog has only about 40 subjects.

**Optional bonus:** Isolation Forest anomaly detection on marks to flag unusual entries, complementing the audit log.

**AI UI elements:** At-Risk Dashboard (sortable, filterable, with the Run button), risk badges on student profiles and faculty class lists, Model Insights page (metrics, confusion matrix, ROC, feature importance), a what-if panel with sliders calling `/predict`, a Recommendations tab, and a smart search box with a LIKE comparison toggle.

## 6. Requirement traceability matrix

| Requirement from the brief | Where it is satisfied |
| --- | --- |
| Entities, attributes, relationships, business rules | A1 |
| ER/EER model converted to a relational schema | A1 |
| Primary, candidate and foreign keys | A2 |
| Functional dependencies and normalization to 3NF/BCNF | A2 |
| DDL and DML | A3 |
| Joins, nested and correlated queries, subqueries, aggregates, GROUP BY, HAVING | A3 query table |
| Sufficient and realistic sample data | A3 seed generator |
| Views and indexes | A4 |
| Triggers and/or stored procedures | A4 |
| Transactions and ACID properties | A4 ACID Lab |
| Concurrency discussion | A4 Concurrency Lab and written report section |
| Basic security and role-based access control | A4 DB roles, JWT, BCrypt, injection demo |
| Web front end connected to the database | Part B |
| Insert, search, update, delete, report generation | Part B CRUD pages and Reports page |
| SQL queries and results illustrated | Part B SQL Trace panel |
| Button or link to the ER model/schema | Part B navbar schema page |
| Flow Front End → SQL Query → Database → Result | Part B trace flow diagram |
| DBMS remains the primary focus | Section 2 design principle |
| Meaningful AI/ML task on project data | Part C at-risk prediction |
| Preprocessing, model selection, training, testing, evaluation | Part C |
| AI/ML integrated with database and front end | Part C integration flow |
| Useful intelligent feature (prediction, recommendation, intelligent search) | Part C prediction, recommendation and smart search |

## 7. Project structure

```
project/
├── backend/       Spring Boot (controller, service, repository, config, security, trace)
│   └── src/main/resources/db/migration/
│       ├── V1__core_tables.sql
│       ├── V2__academic_tables.sql
│       ├── V3__alter_circular_fk.sql
│       ├── V4__indexes.sql
│       ├── V5__reference_data.sql
│       ├── R__views.sql
│       ├── R__functions.sql
│       ├── R__procedures.sql
│       ├── R__triggers.sql
│       └── V6__roles_and_grants.sql
├── frontend/      React (pages, components, api, TracePanel)
├── ai-service/    FastAPI app, training scripts, artifacts
├── database/      seed/generate_data.py, queries/ (22 queries), demo/ (ACID, concurrency, injection, DROP/TRUNCATE)
└── docs/          ER diagram, normalization write-up, report, viva notes
```

## 8. Build order

1. ER/EER diagram and relational schema.
2. Normalization write-up.
3. Flyway V1 and V2 (full DDL), reference data, then the seed generator. The AI part depends on realistic seeded data.
4. The 22 queries, views and indexes with EXPLAIN proof.
5. Functions, procedures, triggers and roles.
6. Spring Boot APIs, JWT security and SQL trace.
7. ACID Lab and Concurrency Lab.
8. React frontend, including the ER/schema page.
9. `v_ml_features`, EDA, training, FastAPI service.
10. Integration, recommendation and smart search, then the final report.

## 9. Final deliverables

- Working application (React, Spring Boot, Python service, MySQL).
- ER/EER diagram, keys and FD tables, normalization write-up.
- All SQL: migrations, 22 labeled queries, demo scripts.
- Screenshots of each demo (queries, triggers, ACID, concurrency, EXPLAIN, roles, SQL Trace, dashboard).
- AI report: methodology, leakage handling, results table, synthetic-data disclaimer.
- Written discussion of concurrency, locking and security limitations.
- Viva question-and-answer notes.

## 10. Known caveats and open decisions

1. **Database engine:** MySQL 8 is assumed. Confirm or switch to PostgreSQL.
2. **DB roles vs. app connection:** the roles are shown with separate DB users, while the app uses one least-privilege user plus JWT checks.
3. **No native row-level security** in MySQL, so per-user filtering is done with views and Spring checks.
4. **Trace coverage:** the wrapper must sit at JdbcTemplate level so procedure calls and AI-related queries are captured.
5. **Labs add pages:** keep the ACID and Concurrency Labs minimal so the UI stays simple.
6. **Python database access:** keep the read-only user, or switch to Spring-exported training data.
7. **Synthetic data:** state it clearly in the report.
8. **Optional extras:** admin SQL console and Isolation Forest anomaly detection, only if time allows.
