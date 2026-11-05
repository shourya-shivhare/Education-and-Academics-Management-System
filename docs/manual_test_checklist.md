# Manual Demonstration Checklist

Use this checklist with the Docker stack and the browser. Do not mark a row
pass until the expected result is visible in the UI, SQL console, Swagger, or
database client. `Screenshot name` is the suggested filename.

| Requirement | How to demonstrate it | Expected result | Pass or fail | Screenshot name |
|---|---|---|---|---|
| ER/EER model | Open Schema from the navbar and show the ER image and explorer | Entities, specialization, recursive prerequisites, and relationships are visible | ____ | `01-schema-er.png` |
| Keys and FDs | Open the schema explorer and explain PK, candidate/unique keys, and FKs for Student, Enrollment, Subject, and Marks | Keys and dependency examples match the schema | ____ | `02-keys-fds.png` |
| Normalization | Explain the flat-record decomposition in the report/viva and show separate Person, Student, Program, Department, Subject, and Enrollment tables | 1NF, 2NF, and 3NF reasoning is clear; BCNF trade-off is acknowledged | ____ | `03-normalization.png` |
| DDL | Show Flyway migration history and representative CREATE/ALTER/constraint statements | Flyway is the only schema changer; constraints exist in MySQL | ____ | `04-ddl-flyway.png` |
| DML | Create, edit, and delete a permitted Subject or Faculty record | Row is inserted/updated/deleted and the UI shows the result | ____ | `05-dml-crud.png` |
| Joins | Run report query q01 from Reports | Multi-table transcript rows are returned | ____ | `06-joins-report.png` |
| Subqueries | Run the report query that uses a nested/correlated subquery | Correct filtered result is returned | ____ | `07-subquery-report.png` |
| Aggregates | Run department performance/toppers with GROUP BY/HAVING | Aggregate values and grouped results are shown | ____ | `08-aggregates.png` |
| Views | Open Schema and Reports; show `v_student_transcript`, `v_dept_performance`, and `v_ml_features` | Derived data is read from views, not duplicated application logic | ____ | `09-views.png` |
| Indexes | Show indexes in Schema or `SHOW INDEX FROM enrollment` in the SQL console | PK, unique, FK/search indexes are present | ____ | `10-indexes.png` |
| Triggers | Attempt an invalid enrollment, over-capacity enrollment, or invalid marks update | HTTP 409 alert quotes the MySQL trigger/SIGNAL message | ____ | `11-trigger-error.png` |
| Procedures | Use Enrollment, Marks, or Lab actions that call stored procedures | Procedure result is correct and CALL appears in SQL Trace | ____ | `12-procedure-trace.png` |
| Transactions and ACID | Run each ACID Lab scenario, including forced failure | Atomicity rolls back; consistency rejects invalid data; isolation/durability result is logged | ____ | `13-acid-lab.png` |
| Concurrency | Run last-seat race, lost update, and deadlock scenarios | Safe locking prevents oversubscription; conflict/deadlock is explained | ____ | `14-concurrency-lab.png` |
| Security and RBAC | Log in as Admin, Registrar, Faculty, and Student; attempt permitted and forbidden routes | JWT role checks return expected access; student/faculty data is scoped | ____ | `15-rbac.png` |
| CRUD UI | Visit Students, Faculty, Subjects, Offerings, Enrollment, Attendance, and Marks | Search, pagination, validation, insert/update/delete, confirmation, and errors work | ____ | `16-crud-pages.png` |
| SQL trace | Perform a CRUD/report action and open its TracePanel | SQL template, bound params, rows affected, and time are shown | ____ | `17-sql-trace.png` |
| ER/schema button | Click `View ER / Schema` in the top bar | `/schema` opens without manually typing the URL | ____ | `18-schema-button.png` |
| Frontend → SQL → DB → Result | Create a record, inspect TracePanel, then refresh/search it | Browser action reaches Spring, parameterized SQL reaches MySQL, and result returns in the envelope | ____ | `19-end-to-end-flow.png` |
| AI preprocessing | Open `ai-service/training/eda.ipynb` and run cells | Imputation, IQR clipping, encoding, scaling, distributions, heatmap, and leakage note appear | ____ | `20-eda.png` |
| Model selection | Run `python training/train.py` with the synthetic dataset | Logistic Regression, Random Forest, and HistGradientBoosting are compared with 5-fold CV | ____ | `21-model-selection.png` |
| Training | Run the training command and inspect `artifacts/model.joblib` and `model_meta.json` | Pipeline, calibrated model, threshold, feature list, and version are saved | ____ | `22-training-artifacts.png` |
| Testing/validation | Use Swagger `/docs` with valid, malformed, missing-key, and unknown-student requests | Valid requests succeed; malformed/auth/unknown data produce clear errors | ____ | `23-swagger-validation.png` |
| Evaluation | Open metrics and generated ROC, PR, confusion matrix, and permutation-importance artifacts | Required metrics and plots exist; synthetic-data disclaimer is visible | ____ | `24-evaluation.png` |
| AI/database integration | Run `/api/risk/run?term=N` as an allowed role | `v_ml_features` is traced, predictions are sent internally, and `student_risk` is upserted | ____ | `25-risk-run-trace.png` |
| AI/frontend integration | Open `/risk`, run analysis, use filters, what-if sliders, recommendations, and subject AI search | Dashboard, badges, model insights, recommendations, and smart search work through Spring | ____ | `26-risk-dashboard.png` |

## Manual commands

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Open `http://localhost:5173`. The AI service is internal to the Compose
network; use `http://localhost:8001/health` only for local diagnostics.
