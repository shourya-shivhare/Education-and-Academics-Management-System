# Project Report Outline

## 1. Introduction

- Problem statement, scope, users, and DBMS-first objective.
- Why MySQL is the source of truth.
- Screenshot: `01-schema-er.png`.

## 2. Requirements and Architecture

- React/Vite browser, Spring Boot/JdbcTemplate API, MySQL/InnoDB, and internal
  FastAPI service.
- Browser → Spring → database flow and Spring → internal AI flow.
- Screenshot: `19-end-to-end-flow.png`.

## 3. ER/EER Model and Relational Mapping

- Entities, attributes, relationships, Person specialization, multivalued
  phones, derived attributes, recursive prerequisites, and Enrollment as the
  M:N relationship resolver.
- Convert the conceptual model into tables, keys, and foreign keys.
- Screenshot: `01-schema-er.png`.

## 4. Keys, Functional Dependencies, and Normalization

- Primary, candidate, foreign, and composite keys.
- 1NF removes repeating/non-atomic values; 2NF removes partial dependencies;
  3NF removes transitive dependencies.
- Discuss the BCNF example `Teaching(student, subject, faculty)`, its
  decomposition, losslessness, and dependency-preservation trade-off.
- Screenshot: `02-keys-fds.png`, `03-normalization.png`.

## 5. DDL, DML, Queries, Views, and Indexes

- Flyway migrations, constraints, DML, joins, nested/correlated subqueries,
  aggregates, GROUP BY/HAVING, views, and indexes.
- Explain why schema changes are not performed by Hibernate or runtime code.
- Screenshots: `04-ddl-flyway.png`, `06-joins-report.png`,
  `07-subquery-report.png`, `08-aggregates.png`, `09-views.png`,
  `10-indexes.png`.

## 6. Database Business Rules

- Triggers for cross-row/time-overlap rules and SIGNAL-to-409 behavior.
- Procedures for transactional workflows such as enrollment and marks.
- Screenshot: `11-trigger-error.png`, `12-procedure-trace.png`.

## 7. Transactions and ACID

- Atomicity: all statements in a workflow commit or roll back.
- Consistency: constraints, triggers, and procedures prevent invalid states.
- Isolation: concurrent transactions observe data according to the selected
  isolation level.
- Durability: committed InnoDB data survives a restart.
- Screenshot: `13-acid-lab.png`.

## 8. Concurrency Discussion

### Locking

InnoDB uses row and index locks. A transaction that checks a capacity or reads
a row before updating it must lock the relevant row so two sessions cannot both
claim the last seat. `SELECT ... FOR UPDATE` is used when the read is part of a
write decision: it locks the selected rows until commit/rollback. It is not
needed for a read-only report.

### Isolation levels

`READ COMMITTED` reduces stale reads but allows a later statement to see a
different committed value. `REPEATABLE READ`, MySQL's default, gives a stable
consistent snapshot for ordinary reads and uses next-key locking for locking
reads. `SERIALIZABLE` gives the strongest ordering but reduces concurrency.
The lab demonstrates the observable difference rather than claiming that one
level is always best.

### Deadlocks

A deadlock occurs when transaction A holds lock X and waits for Y while
transaction B holds Y and waits for X. InnoDB detects the cycle and rolls back
one victim. The correct application response is a clear error and, where
appropriate, a bounded retry of the complete transaction; partial statements
must not be retried outside their transaction.

### Optimistic locking

Marks include a version/value check in the update predicate. If the affected
row count is zero, another writer won and the API returns 409, asking the user
to reload. This avoids holding locks during a long browser edit.

- Screenshot: `14-concurrency-lab.png`.

## 9. Security and RBAC

- JWT authentication and method-level role checks.
- Flyway user for migrations, runtime app user for normal database access, and
  `ml_readonly` for AI reads.
- Parameterized SQL and the isolated SQL-injection demonstration.
- MySQL does not provide native row-level security like PostgreSQL. Therefore
  the design uses one runtime database user plus Spring JWT checks and
  parameterized predicates to scope Faculty to their offerings and Students to
  their own records. This is an application-enforced boundary, not a
  database-native row-security policy; direct credentials to the runtime user
  would bypass those Spring predicates.
- The AI service is not browser-facing and requires `X-Internal-Key`.
- Screenshot: `15-rbac.png`.

## 10. CRUD UI and SQL Trace

- Search, pagination, validation, confirmation, trigger alerts, optimistic
  conflict prompts, and the shared TracePanel.
- Explain the envelope `{data, trace}` and how it makes DB activity observable.
- Screenshots: `05-dml-crud.png`, `17-sql-trace.png`, `18-schema-button.png`.

## 11. ML Methodology

- One row per student-term mid-term snapshot.
- Numeric preprocessing: median imputation and IQR clipping.
- Categorical preprocessing: one-hot encoding for program and year of study.
- StandardScaler for numeric features.
- Logistic Regression, Random Forest, and HistGradientBoostingClassifier.
- Stratified 5-fold CV; compare class weighting with SMOTE applied only inside
  training folds.
- Time-based split: earlier terms train, latest term is held out, preventing
  future-term information from entering training.
- Calibrated probabilities and a threshold tuned on a validation fold.
- Select by at-risk recall subject to a minimum precision floor because missing
  an at-risk student is more costly than reviewing a false positive.
- Screenshot: `20-eda.png`, `21-model-selection.png`, `22-training-artifacts.png`.

## 12. Leakage Handling and Label Definition

- Inputs include only information available at mid-term:
  attendance, internal marks, assignments, prior GPA/CGPA, backlog and fee
  delay signals, credits, program, year, trend, and first-year status.
- Final grade F and end-of-term detention are label-only in `v_ml_labels` and
  are not features in `v_ml_features`.
- Do not use final marks, final grade, detention outcome, or post-term
  attendance in prediction.
- Screenshot: `20-eda.png`.

## 13. Synthetic-Data Disclaimer and Results

All current training data is deterministic synthetic coursework data. It is
useful for repeatable demonstrations and pipeline validation, not evidence of
real student performance, fairness, calibration, or deployment readiness. The
perfect-looking metrics are expected from the deliberately separable seed and
must not be reported as production accuracy.

| Metric | Held-out synthetic latest term |
|---|---:|
| Rows | 30 |
| Accuracy | 1.000 |
| Precision | 1.000 |
| Recall | 1.000 |
| F1 | 1.000 |
| ROC-AUC | 1.000 |
| PR-AUC | 1.000 |
| Threshold | 0.8692 |
| Data note | Synthetic coursework data only |

Screenshots: `24-evaluation.png`, `25-risk-run-trace.png`,
`26-risk-dashboard.png`.

## 14. Limitations and Future Work

- Replace synthetic data with approved, representative data.
- Add monitoring for drift, calibration, fairness, and data-quality failures.
- Move secrets to deployment secret storage.
- Consider database-native views/procedures or a separate tenant/security
  boundary if direct database access must be exposed to untrusted clients.

## 15. Conclusion and Viva Demonstration

- Follow the checklist in `manual_test_checklist.md`.
- Finish with the end-to-end flow and the concurrency/security limitations.
