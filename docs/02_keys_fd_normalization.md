# Education and Academics Management System — Keys, Functional Dependencies, and Normalization

## 1. Introduction

This document is Part A2 of the coursework: the **primary-key, candidate-key and
foreign-key** declarations, the **non-trivial functional dependency (FD) set** for every
relation, a **worked normalization** of one flat table through 1NF/2NF/3NF, a **BCNF
counter-example** with a lossless-but-non-FD-preserving decomposition, and a note on the one
**deliberate denormalization** in the schema. The relation and column names, the primary
keys, the `UNIQUE` alternates (candidate keys), the foreign keys, and the denormalized
`final_grade` / `is_detained` on `enrollment` are taken directly from the conceptual design
in [`docs/01_er_eer_design.md`](./01_er_eer_design.md); this document only adds the explicit
FDs and candidate keys that the keys/uniques of doc 01 already imply. MySQL 8 (InnoDB) is the
source of truth; derived attributes (`age`, `attendance_percent`, `cgpa`, `grade_letter`,
`enrolled_count`) are **not stored**, so they never appear in any FD below.

**Notation legend.** `X -> Y` reads "X functionally determines Y" (a functional dependency);
**PK** = primary key (the chosen candidate key); **CK** = candidate key (any minimal
superkey); a **superkey** is any attribute set that functionally determines all attributes
of the relation (a CK is a minimal superkey). Composite keys are written in parentheses,
e.g. `(student_id, offering_id)`. Curly braces group a set of attributes on the right-hand
side of an FD. An FD is **non-trivial** when the right-hand side is not a subset of the
left-hand side; only non-trivial FDs are listed. A **prime** attribute is one that belongs
to some candidate key.

---

## 2. Keys, Foreign Keys, and Functional Dependencies per Relation

### 2.1 Summary table — Relation | PK | Candidate Keys | Foreign Keys

| # | Relation | Primary Key | Candidate Keys (incl. PK) | Foreign Keys (→ referenced) |
| --- | --- | --- | --- | --- |
| 1 | `department` | `department_id` | `{department_id}`, `{name}` | `hod_faculty_id` → `faculty.faculty_id` (nullable, circular) |
| 2 | `program` | `program_id` | `{program_id}` | `department_id` → `department.department_id` |
| 3 | `person` | `person_id` | `{person_id}`, `{email}` | — (base table; `age` is derived, not stored) |
| 4 | `person_phone` | `(person_id, phone_no)` | `{(person_id, phone_no)}` | `person_id` → `person.person_id` |
| 5 | `student` | `student_id` | `{student_id}`, `{roll_no}` | `student_id` → `person.person_id` (IS-A); `program_id` → `program.program_id` |
| 6 | `faculty` | `faculty_id` | `{faculty_id}`, `{employee_no}` | `faculty_id` → `person.person_id` (IS-A); `department_id` → `department.department_id` |
| 7 | `subject` | `subject_id` | `{subject_id}`, `{code}` | `department_id` → `department.department_id`; `program_id` → `program.program_id` (nullable) |
| 8 | `subject_prerequisite` | `(subject_id, prereq_subject_id)` | `{(subject_id, prereq_subject_id)}` | `subject_id` → `subject.subject_id`; `prereq_subject_id` → `subject.subject_id` |
| 9 | `term` | `term_id` | `{term_id}`, `{name}` | — |
| 10 | `classroom` | `room_id` | `{room_id}`, `{(building, room_no)}` | — |
| 11 | `course_offering` | `offering_id` | `{offering_id}`, `{(subject_id, faculty_id, term_id, section)}` | `subject_id` → `subject.subject_id`; `faculty_id` → `faculty.faculty_id`; `term_id` → `term.term_id` |
| 12 | `timetable_slot` | `slot_id` | `{slot_id}` | `offering_id` → `course_offering.offering_id`; `room_id` → `classroom.room_id` |
| 13 | `enrollment` | `enrollment_id` | `{enrollment_id}`, `{(student_id, offering_id)}` | `student_id` → `student.student_id`; `offering_id` → `course_offering.offering_id` |
| 14 | `attendance` | `attendance_id` | `{attendance_id}`, `{(enrollment_id, class_date)}` | `enrollment_id` → `enrollment.enrollment_id` |
| 15 | `assessment` | `(offering_id, assessment_no)` | `{(offering_id, assessment_no)}` | `offering_id` → `course_offering.offering_id` |
| 16 | `marks` | `(enrollment_id, offering_id, assessment_no)` | `{(enrollment_id, offering_id, assessment_no)}` | `enrollment_id` → `enrollment.enrollment_id`; `(offering_id, assessment_no)` → `assessment.(offering_id, assessment_no)` (composite FK) |
| 17 | `grade_scale` | `grade_id` | `{grade_id}`, `{grade_letter}` | — (soft lookup, no hard FK) |
| 18 | `fee_payment` | `payment_id` | `{payment_id}` | `student_id` → `student.student_id`; `term_id` → `term.term_id` (nullable) |
| 19 | `users` | `user_id` | `{user_id}`, `{username}` | `person_id` → `person.person_id` (nullable) |
| 20 | `audit_log` | `log_id` | `{log_id}` (append-only, no natural key) | `user_id` → `users.user_id` (nullable) |
| 21 | `student_risk` | `risk_id` | `{risk_id}`, `{(student_id, term_id, computed_at)}` (optional business CK) | `student_id` → `student.student_id`; `term_id` → `term.term_id` (nullable) |

> IS-A note (relations 5 and 6): under the PK-also-FK specialization mapping, `student.student_id` *is* `person.person_id` and `faculty.faculty_id` *is* `person.person_id` (same value). The FDs below list `person_id` for completeness; the dependency `student_id -> person_id` (and `faculty_id -> person_id`) is therefore an **identity (trivial) dependency** expressing the subtype→supertype link, not an extra determinant.

### 2.2 Per-relation non-trivial FD sets

**1. `department`** — PK `department_id`; CKs `{department_id}`, `{name}`.
- `department_id` -> `{name, hod_faculty_id, established_year}`
- `name` -> `department_id`  *(justifies `name` as a candidate key)*
- `hod_faculty_id` is a nullable, circular FK → `faculty.faculty_id` (set by a stored procedure after both rows exist); it does **not** determine anything in `department` (a faculty can be HOD of at most one department, but the column is on the department side).

**2. `program`** — PK `program_id`; CK `{program_id}` (no natural unique alternate in doc 01).
- `program_id` -> `{name, degree, duration_years, department_id, total_credits}`

**3. `person`** — PK `person_id`; CKs `{person_id}`, `{email}`. Base table, no FK.
- `person_id` -> `{first_name, last_name, email, date_of_birth, gender, address_line, city, state, pin_code}`  *(stored columns only; `age` is derived from `date_of_birth`, not stored)*
- `email` -> `person_id`  *(justifies `email` as a candidate key)*

**4. `person_phone`** — PK `(person_id, phone_no)`; CK `{(person_id, phone_no)}`. FK `person_id` → `person.person_id`.
- `(person_id, phone_no)` -> `phone_type`

**5. `student`** — PK `student_id`; CKs `{student_id}`, `{roll_no}`. FKs `student_id` → `person.person_id` (IS-A), `program_id` → `program.program_id`.
- `student_id` -> `{roll_no, program_id, admission_date, status, current_semester, person_id}`  *(`person_id` = `student_id` under IS-A, so this part is an identity; `cgpa` is derived, not stored)*
- `roll_no` -> `student_id`  *(justifies `roll_no` as a candidate key)*

**6. `faculty`** — PK `faculty_id`; CKs `{faculty_id}`, `{employee_no}`. FKs `faculty_id` → `person.person_id` (IS-A), `department_id` → `department.department_id`.
- `faculty_id` -> `{employee_no, department_id, designation, hire_date, max_load, person_id}`  *(`person_id` = `faculty_id` under IS-A, an identity)*
- `employee_no` -> `faculty_id`  *(justifies `employee_no` as a candidate key)*

**7. `subject`** — PK `subject_id`; CKs `{subject_id}`, `{code}`. FKs `department_id` → `department.department_id`, `program_id` → `program.program_id` (nullable).
- `subject_id` -> `{code, title, credits, department_id, program_id, semester}`
- `code` -> `subject_id`  *(justifies `code` as a candidate key)*

**8. `subject_prerequisite`** — PK `(subject_id, prereq_subject_id)`; CK `{(subject_id, prereq_subject_id)}`. FKs both → `subject.subject_id`. CHECK `subject_id <> prereq_subject_id`.
- `(subject_id, prereq_subject_id)` -> `min_grade`

**9. `term`** — PK `term_id`; CKs `{term_id}`, `{name}`. No FK.
- `term_id` -> `{name, start_date, end_date, is_current}`
- `name` -> `term_id`  *(justifies `name` as a candidate key)*

**10. `classroom`** — PK `room_id`; CKs `{room_id}`, `{(building, room_no)}`. No FK.
- `room_id` -> `{building, room_no, capacity}`
- `(building, room_no)` -> `room_id`  *(justifies `(building, room_no)` as a candidate key)*

**11. `course_offering`** — PK `offering_id`; CKs `{offering_id}`, `{(subject_id, faculty_id, term_id, section)}`. FKs `subject_id` → `subject.subject_id`, `faculty_id` → `faculty.faculty_id`, `term_id` → `term.term_id`.
- `offering_id` -> `{subject_id, faculty_id, term_id, section, capacity, status}`  *(`enrolled_count` is derived, not stored)*
- `(subject_id, faculty_id, term_id, section)` -> `{offering_id, capacity, status}`  *(the natural key determines the surrogate and all attributes; justifies it as a candidate key)*

**12. `timetable_slot`** — PK `slot_id`; CK `{slot_id}`. FKs `offering_id` → `course_offering.offering_id`, `room_id` → `classroom.room_id`.
- `slot_id` -> `{offering_id, room_id, day_of_week, start_time, end_time}`
- *Note:* the **no-faculty / no-room double-booking** rule (a faculty, and a classroom, cannot be in two slots whose `[start_time, end_time)` windows overlap on the same `day_of_week`) is a **trigger, not a candidate key**. Time-overlap is a predicate over an interval, which cannot be expressed as a uniqueness key, so it cannot be a CK.

**13. `enrollment`** — PK `enrollment_id`; CKs `{enrollment_id}`, `{(student_id, offering_id)}`. FKs `student_id` → `student.student_id`, `offering_id` → `course_offering.offering_id`.
- `enrollment_id` -> `{student_id, offering_id, enroll_date, status, final_grade, is_detained}`
- `(student_id, offering_id)` -> `{enrollment_id, enroll_date, status, final_grade, is_detained}`  *(the natural key determines the surrogate and all attributes; justifies it as a candidate key)*
- *Note:* `final_grade` and `is_detained` are **deliberate denormalizations** (see §5). They introduce transitive dependencies (`enrollment_id` -> weighted(`marks`) -> `final_grade`), so `enrollment` is intentionally not in 3NF/BCNF on those two attributes.

**14. `attendance`** — PK `attendance_id` (surrogate); CKs `{attendance_id}`, `{(enrollment_id, class_date)}`. FK `enrollment_id` → `enrollment.enrollment_id`.
- `attendance_id` -> `{enrollment_id, class_date, status}`
- `(enrollment_id, class_date)` -> `{attendance_id, status}`  *(justifies `(enrollment_id, class_date)` as a candidate key; `attendance_percent` is derived per enrollment, not stored)*

**15. `assessment`** — PK `(offering_id, assessment_no)`; CK `{(offering_id, assessment_no)}`. FK `offering_id` → `course_offering.offering_id`. (Weak entity; `assessment_no` is a partial key, unique only within an offering.)
- `(offering_id, assessment_no)` -> `{title, max_marks, weightage, assessment_date}`

**16. `marks`** — PK `(enrollment_id, offering_id, assessment_no)`; CK `{(enrollment_id, offering_id, assessment_no)}`. FKs `enrollment_id` → `enrollment.enrollment_id`, `(offering_id, assessment_no)` → `assessment.(offering_id, assessment_no)` (composite FK).
- `(enrollment_id, offering_id, assessment_no)` -> `marks_obtained`  *(`grade_letter` is derived via `grade_scale`, not stored)*
- *Footnote:* `offering_id` is kept in the PK to satisfy the **composite FK to `assessment`** `(offering_id, assessment_no)`. Strictly, `enrollment_id` -> `offering_id` (via `enrollment`), so `offering_id` is **non-essential** to identifying a marks row — `(enrollment_id, assessment_no)` would already be a key. Carrying `offering_id` in the PK is therefore a **minor controlled redundancy for FK convenience** (so the composite FK column set is a leftmost prefix of an index), not a normalization violation of the marks relation itself.

**17. `grade_scale`** — PK `grade_id`; CKs `{grade_id}`, `{grade_letter}`. No FK (soft lookup, matched by percentage in views/procedures).
- `grade_id` -> `{grade_letter, min_percent, max_percent, grade_point, is_pass}`
- `grade_letter` -> `grade_id`  *(justifies `grade_letter` as a candidate key)*

**18. `fee_payment`** — PK `payment_id`; CK `{payment_id}` (no natural unique alternate). FKs `student_id` → `student.student_id`, `term_id` → `term.term_id` (nullable).
- `payment_id` -> `{student_id, term_id, amount, payment_date, payment_mode, status}`

**19. `users`** — PK `user_id`; CKs `{user_id}`, `{username}`. FK `person_id` → `person.person_id` (nullable).
- `user_id` -> `{person_id, username, password_hash, role, is_active, last_login}`
- `username` -> `user_id`  *(justifies `username` as a candidate key)*

**20. `audit_log`** — PK `log_id`; CK `{log_id}` (append-only, no natural key). FK `user_id` → `users.user_id` (nullable).
- `log_id` -> `{user_id, action, table_name, row_pk, old_values, new_values, timestamp}`

**21. `student_risk`** — PK `risk_id`; CKs `{risk_id}`, `{(student_id, term_id, computed_at)}` (optional business CK: one row per prediction run per student per term). FKs `student_id` → `student.student_id`, `term_id` → `term.term_id` (nullable).
- `risk_id` -> `{student_id, term_id, risk_score, risk_label, contributing_factors, computed_at}`
- `(student_id, term_id, computed_at)` -> `{risk_id, risk_score, risk_label, contributing_factors}`  *(justifies the optional business candidate key)*

---

## 3. Worked Normalization from One Flat Table (UNF → 1NF → 2NF → 3NF)

Start from a single denormalized report table a registrar might export, containing a
student's subjects and, within each subject, its assessments:

```
Student( student_id, student_name, program, dept, dept_head,
         { ( subject_code, subject_name, credits, faculty, term,
             { ( assessment, marks ) } ) } )
```

### 3.1 UNF (unnormalized form)

The table has **nested repeating groups**: a student has many subjects, and each subject has
many assessments. The cells for `subject_code … term` and for `assessment, marks` are
list-valued (non-atomic). **Violation: repeating groups / non-atomic multi-valued cells** →
not in 1NF.

### 3.2 1NF — remove repeating groups

Flatten the repeating groups to atomic rows, one row per `(student, subject, term, assessment)`:

```
R1( student_id, student_name, program, dept, dept_head,
    subject_code, subject_name, credits, faculty, term, assessment, marks )
PK = ( student_id, subject_code, term, assessment )
```

**Violation removed:** repeating groups (non-atomic multi-valued cells) — every cell now holds a single atomic value.

The FD set on R1 (only the non-trivial ones):

```
student_id                            -> { student_name, program }
program                               -> dept
dept                                  -> dept_head
subject_code                          -> { subject_name, credits }
(subject_code, term)                  -> faculty
(student_id, subject_code, term, assessment) -> marks
```

### 3.3 2NF — remove partial dependencies

A relation is in 2NF iff it is in 1NF and **no non-prime attribute depends on a proper subset
of the composite primary key**. R1's PK is `(student_id, subject_code, term, assessment)`.
Three partial dependencies exist:

1. **`student_id` -> `{student_name, program, dept, dept_head}`** — these depend only on the
   `student_id` *part* of the PK (a proper subset).
2. **`subject_code` -> `{subject_name, credits}`** — depends only on the `subject_code` *part*.
3. **`(subject_code, term)` -> `faculty`** — depends on `subject_code + term`, still a proper
   subset of the four-attribute PK.

Project R1 into four relations, each keyed by the determinant of the removed partial
dependency, keeping the full PK only where marks actually need all four parts:

```
Enroll_Marks( student_id, subject_code, term, assessment, marks )
    PK ( student_id, subject_code, term, assessment )

Student2( student_id, student_name, program, dept, dept_head )
    PK student_id

Subject2( subject_code, subject_name, credits )
    PK subject_code

Offering2( subject_code, term, faculty )
    PK ( subject_code, term )
```

**Violations removed:** the three partial dependencies (i), (ii), (iii) above. Each relation
is now in 2NF (every non-prime attribute depends on the *whole* of its relation's PK).

### 3.4 3NF — remove transitive dependencies

A relation is in 3NF iff it is in 2NF and **no non-prime attribute is transitively dependent
on a candidate key**. Look at `Student2(student_id, student_name, program, dept, dept_head)`,
PK `student_id`:

```
student_id -> program -> dept -> dept_head
```

Two transitive dependencies:

1. **`program` -> `dept` is transitive** (`student_id` -> `program` -> `dept`); `dept` is
   non-prime and not dependent on `student_id` alone through a single step.
2. **`dept` -> `dept_head` is transitive** (`student_id` -> `dept` -> `dept_head`); `dept_head`
   is non-prime.

Decompose `Student2` along these transitivities:

```
Student( student_id, student_name, program )     PK student_id
Program( program, dept )                         PK program
Department( dept, dept_head )                     PK dept
```

**Violations removed:** the two transitive dependencies (i) and (ii). Every non-prime
attribute now depends on its key, the whole key, and nothing but the key → **3NF achieved**.

### 3.5 Final normalized relation set (6 relations)

```
Enroll_Marks( student_id, subject_code, term, assessment, marks )  PK ( student_id, subject_code, term, assessment )
Student( student_id, student_name, program )                        PK student_id
Program( program, dept )                                            PK program
Department( dept, dept_head )                                        PK dept
Subject( subject_code, subject_name, credits )                      PK subject_code
Offering( subject_code, term, faculty )                              PK ( subject_code, term )
```

**One-line BCNF note:** each of the six is also in **BCNF**, because in every relation the
left-hand side of every non-trivial FD is a candidate key of that relation (no non-key
determinant of a non-prime attribute remains).

### 3.6 Mapping back to the real doc-01 schema

| Worked-example relation | Real doc-01 schema relation | Note |
| --- | --- | --- |
| `Enroll_Marks` | `marks` (+ `enrollment`) | `marks` holds one row per enrollment×assessment; `enrollment` carries the `(student, offering)` link. |
| `Student` | `student` | `student` adds `roll_no`, `admission_date`, `status`, `current_semester`; `student_name` is `person.first_name`+`last_name`. |
| `Program` | `program` | `program` adds `degree`, `duration_years`, `department_id` (→ `department`), `total_credits`. |
| `Department` | `department` | `department` adds `department_id` (surrogate), `established_year`; `dept_head` becomes the FK `hod_faculty_id` → `faculty.faculty_id`. |
| `Subject` | `subject` | `subject` adds `subject_id` (surrogate), `department_id`, `program_id` (nullable), `semester`. |
| `Offering` | `course_offering` | `course_offering` adds the `section` discriminator and surrogate `offering_id`; the real natural key is `(subject_id, faculty_id, term_id, section)`. |

---

## 4. BCNF Example — `Teaching(student, subject, faculty)`

Consider the relation

```
Teaching( student, subject, faculty )
```

with the FD set

```
F = { (student, subject) -> faculty,
      faculty            -> subject }
```

### 4.1 Candidate keys

- **`{student, subject}`** is a candidate key: its closure under F is
  `(student, subject)+ = {student, subject, faculty}` (apply `(student, subject) -> faculty`),
  which is all attributes. It is minimal (neither `student` nor `subject` alone determines
  `faculty`).
- **`{student, faculty}`** is also a candidate key: `(student, faculty)+ = {student, faculty}`,
  then `faculty -> subject` adds `subject`, giving all attributes. It is minimal too.

So **candidate keys = `{ {student, subject}, {student, faculty} }`**. All three attributes
(`student`, `subject`, `faculty`) are **prime** (each appears in at least one candidate key).

### 4.2 3NF proof (Teaching IS in 3NF)

A relation is in 3NF iff for every non-trivial FD `X -> A`, either `X` is a superkey **or**
`A` is prime. Check each non-trivial FD:

- `(student, subject) -> faculty`: the LHS `{student, subject}` **is a superkey** (it is a
  candidate key). ✓
- `faculty -> subject`: the LHS `{faculty}` is **not** a superkey, but the RHS `subject` is
  **prime** (it is in the candidate key `{student, subject}`). ✓

Both FDs satisfy the 3NF condition ⇒ **`Teaching` is in 3NF**.

### 4.3 BCNF failure (Teaching is NOT in BCNF)

BCNF requires **every** non-trivial FD `X -> A` to have `X` a superkey. The FD
`faculty -> subject` has LHS `{faculty}`, which is **not** a superkey (its closure is
`{faculty, subject}`, missing `student`). ⇒ **BCNF is violated**.

**Therefore `Teaching` is in 3NF but NOT in BCNF** — the classic "all attributes prime but a
non-key determinant exists" case.

### 4.4 Decomposition on the offending FD

Decompose on the BCNF-violating FD `faculty -> subject`:

```
R1( student, faculty )      candidate key {student, faculty}
R2( faculty,  subject )     candidate key {faculty}
```

- **R1** has only trivial FDs (`student, faculty` -> anything is trivial since the relation is
  just these two attributes) and the key `{student, faculty}` is a superkey ⇒ R1 is in BCNF.
- **R2** has the single non-trivial FD `faculty -> subject` with LHS `{faculty}` a superkey
  (it is the candidate key) ⇒ R2 is in BCNF.

Both projections are in BCNF.

### 4.5 Lossless-join proof

`R1 ∩ R2 = {faculty}`, and `{faculty}` is a **candidate key of R2** (`faculty -> subject`
covers all of R2). By the **lossless-join criterion** (the common attribute set of two
decomposed relations must be a candidate key of at least one of them), `R1 ⋈ R2` reconstructs
`Teaching` exactly, with no spurious tuples and no lost tuples. ⇒ **The decomposition is
lossless.**

### 4.6 FD preservation — one FD is LOST

Project F onto each projection:

- Onto **R1(student, faculty)**: neither `(student, subject) -> faculty` (needs `subject`,
  absent from R1) nor `faculty -> subject` (needs `subject`, absent) survives. **No non-trivial
  FD of F is preserved in R1.**
- Onto **R2(faculty, subject)**: `faculty -> subject` survives; `(student, subject) -> faculty`
  does **not** (needs `student`, absent from R2).

Preserved FDs = `{ faculty -> subject }`. The FD **`(student, subject) -> faculty` is NOT
preserved** — there is no single decomposed relation containing all three of `student`,
`subject`, `faculty`, so it cannot be checked locally.

**Concrete counter-example** showing the lost FD cannot be enforced from the decomposed
tables. Take `student = S1`, two faculty `F1` and `F2`, with both `F1 -> Sub1` and `F2 -> Sub1`
(this is allowed in R2, since `faculty -> subject` does **not** imply `subject -> faculty`).
Insert `(S1, F1)` in R1 and `(S1, F2)` in R1, and `(F1, Sub1)`, `(F2, Sub1)` in R2. The
lossless join `R1 ⋈ R2` yields:

```
(S1, F1, Sub1)   and   (S1, F2, Sub1)
```

i.e. the **same `(student, subject) = (S1, Sub1)` with two different faculty** — which
**violates** `(student, subject) -> faculty`. Each decomposed table is individually legal, yet
the join breaks the original FD, proving it is **not preserved**.

**Conclusion:** the decomposition into `R1(student, faculty)` and `R2(faculty, subject)` is
**lossless** but does **not preserve** the FD `(student, subject) -> faculty`. This is the
textbook trade-off: BCNF can force a lossless-but-non-FD-preserving split when the offending
determinant determines a prime attribute.

---

## 5. Note on the Deliberate Denormalization of `final_grade` on `enrollment`

- **What is denormalized.** `final_grade` (the grade letter, a `varchar(2)` on `enrollment`)
  is fully determined by the student's `marks` rows, aggregated using each `assessment`'s
  `weightage` and then mapped through `grade_scale`. Storing it on `enrollment` is a
  **transitive redundancy**:

  ```
  enrollment_id -> weighted(marks via assessment.weightage) -> final_grade
  ```

  i.e. a deliberate departure from 3NF/BCNF. (A numeric `final_marks` would be an *optional*
  companion to `final_grade`; this schema, per doc 01, stores only the letter `final_grade` and
  does **not** add `final_marks`, so the denormalization is exactly one column.)

- **Rationale (the trade-off).** Transcript reads are frequent and hot. Caching `final_grade`
  on `enrollment` lets a transcript query read the letter directly, instead of re-aggregating
  every `marks` row and joining `grade_scale` for every transcript row on every read. **Read
  performance is bought at the cost of one controlled redundancy.**

- **DBMS-first consistency.** The cached value is kept consistent **inside MySQL** — by a
  trigger / stored procedure (`sp_compute_final_grades`) that recomputes `final_grade` when
  `marks` change — **not** by application code. Update anomalies are therefore handled in the
  database, not in the Spring Boot layer, consistent with the DBMS-first rule that business
  rules live in MySQL.

- **Contrast with other derived attributes.** Every other derived attribute (`age`,
  `attendance_percent`, `cgpa`, `grade_letter`, `enrolled_count`) is **not stored** — it is
  computed by views and stored functions. `final_grade` is the **single deliberate exception**.
  `is_detained` (the 75% attendance detention flag) follows the **same controlled-denormalization
  pattern and for the same structural reason**: a MySQL trigger cannot modify its own
  triggering `attendance` table, so the derived flag is stored on `enrollment` and refreshed by
  an `AFTER INSERT/UPDATE` trigger on `attendance`. Both `final_grade` and `is_detained` are
  therefore *intentional, DB-managed* denormalizations, not accidental ones.
