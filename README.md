# Education and Academics Management System

College DBMS coursework project for managing students, faculty, subjects, course
offerings, enrollment, attendance, marks, reports, transcripts, and at-risk
student analysis.

The project is intentionally **DBMS-first**:

- MySQL 8/InnoDB is the source of truth.
- Constraints, triggers, stored procedures, views, indexes, roles, and grants
  contain the database business rules.
- Spring Boot calls the database with parameterized SQL through `JdbcTemplate`.
- Flyway is the only schema migration mechanism.
- The browser talks only to Spring Boot.
- The FastAPI service is internal and protected by `X-Internal-Key`.
- API success responses use `{ data, trace }`, where `trace` exposes SQL,
  parameters, affected rows, and execution time for coursework demonstration.
- Validation is manual only. No automated tests are included by design.

## Architecture

```text
Browser
  |
  v
React/Vite served by Nginx :5173
  |
  v  /api proxy
Spring Boot + JdbcTemplate :8081
  |                         |
  |                         v
  |                    FastAPI ML service
  |                    internal Docker network
  v
MySQL 8/InnoDB :3306
```

Runtime database responsibilities:

- `flyway_user`: applies Flyway migrations.
- `app_user`: used by Spring for normal application operations.
- `ml_readonly`: read-only access for the ML feature and label views.
- MySQL owns integrity and business-rule enforcement.

## Main features

### Academic management

- Students, Faculty, Subjects, Course Offerings, Enrollment, Attendance, and
  Marks pages.
- Searchable and paginated tables.
- Insert, update, and delete forms with client-side validation.
- Delete confirmations.
- SQL `TracePanel` below actions.
- Database trigger errors displayed as clear HTTP 409 alerts.
- Optimistic-lock conflict handling for marks with reload guidance.

### Reports and database demonstrations

- Reports catalogue, aggregate reports, charts, and CSV export.
- Student transcript with term filtering, summary metrics, print, CSV export,
  and SQL trace.
- Live schema explorer with tables, columns, keys, foreign keys, indexes, and
  an ER overview.
- ACID and concurrency labs.
- Admin SQL console for the coursework SQL demonstrations.

### Risk and ML integration

- At-Risk Dashboard with filters, sorting, badges, trace output, and a Run Risk
  Analysis action.
- Spring reads `v_ml_features`, calls FastAPI `/predict-batch`, and persists
  results into `student_risk`.
- Model information, metrics, confusion matrix, ROC/PR charts, and feature
  importance.
- What-if prediction panel.
- Recommendations based on TF-IDF and prerequisite filtering.
- Subject smart search with LIKE, FULLTEXT, and AI search modes.
- Risk visibility is role-scoped: faculty see their offerings and students see
  their own records.

## Repository structure

```text
.
├── backend/                 Spring Boot 3, Java 17, JdbcTemplate, Flyway
├── frontend/                React 18, Vite, Bootstrap, Recharts
├── ai-service/              FastAPI service and ML training pipeline
├── database/
│   ├── setup/               Bootstrap database and runtime users
│   ├── seed/                Seed/reference data
│   ├── queries/              Coursework SQL examples
│   └── demo/                Clearly marked SQL-injection demonstration
├── docs/
│   ├── manual_test_checklist.md
│   ├── report_outline.md
│   ├── viva_notes.md
│   └── database design notes
├── docker-compose.yml
└── README.md
```

## Prerequisites

For the recommended Docker workflow:

- Docker Desktop with Docker Compose

For running services individually:

- JDK 17+
- Maven 3.8+ or the included Maven wrapper
- Node.js 18+
- Python 3.10+
- MySQL 8+

## Run the complete stack with Docker

From the repository root:

```powershell
docker compose up -d --build
docker compose ps
```

The compose stack starts:

| Service | Container | Host address |
|---|---|---|
| MySQL 8.4 | `edumanage_mysql` | `localhost:3306` |
| Spring Boot backend | `edumanage_backend` | `http://localhost:8081` |
| React/Nginx frontend | `edumanage_frontend` | `http://localhost:5173` |
| FastAPI AI service | `edumanage_ai` | Internal only; port 8001 inside Docker |

Open the website at:

```text
http://localhost:5173
```

Useful checks:

```powershell
docker compose ps
docker compose logs backend
docker compose logs ai
Invoke-WebRequest http://localhost:8081/api/health
```

The frontend Nginx container proxies `/api/` requests to the backend. The
backend calls the AI container using the Docker service name `ai`; the browser
must not call FastAPI directly.

To stop the stack:

```powershell
docker compose down
```

To remove the development database volume and initialize it from scratch:

```powershell
docker compose down -v
docker compose up -d --build
```

The second command is destructive to local Docker database data. Do not use
`down -v` if you need to preserve local records.

## Development credentials

These are development-only credentials initialized by the backend:

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Faculty | `faculty1` | `faculty123` |
| Student | `student1` | `student123` |
| Registrar | `registrar1` | `reg123` |

Replace these credentials and development secrets before any real deployment.

## Run services individually

### Database

Run the bootstrap script as a MySQL administrator when not using Docker:

```powershell
mysql -u root -p < database/setup/00_create_database_and_users.sql
```

Flyway migrations run when the backend starts.

### Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The backend listens on port `8080` by default outside Docker. In the supplied
Docker compose configuration it is mapped to host port `8081`.

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

The Vite development server normally listens on port `5173`.

### AI service

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Set the database and internal-key environment variables appropriate for the
local installation. The Docker configuration supplies:

```text
ML_DATABASE_URL=mysql+pymysql://ml_readonly:<password>@db:3306/edu_db
ML_INTERNAL_KEY=dev-internal-secret-change-me
```

## AI training workflow

The training pipeline uses:

- Mid-term features from `v_ml_features`.
- A separate post-term label view to avoid label leakage.
- Chronological, time-based train/test splitting.
- Median imputation, IQR clipping, one-hot encoding for program and year, and
  `StandardScaler` in a scikit-learn `Pipeline` and `ColumnTransformer`.
- Comparison of class-weight balancing and SMOTE inside cross-validation folds.
- Logistic Regression, Random Forest, and
  `HistGradientBoostingClassifier`.
- Five-fold `StratifiedKFold` and `GridSearchCV`.
- Recall-focused model selection with a minimum precision floor.
- Validation-fold threshold tuning and probability calibration.

Seed deterministic synthetic data:

```powershell
cd ai-service
$env:ML_SEED_DATABASE_URL = "mysql+pymysql://root:root@localhost:3306/edu_db"
python -m training.seed_synthetic
```

Train and evaluate:

```powershell
$env:ML_DATABASE_URL = "mysql+pymysql://ml_readonly:<password>@localhost:3306/edu_db"
python -m training.train
python -m training.evaluate
```

Artifacts are written under `ai-service/artifacts/`, including the serialized
model, metadata, metrics, comparison data, permutation importance, and ROC/PR
images. The included training data is synthetic and its results are not
evidence of production model performance.

## API and manual verification

FastAPI Swagger is available inside the AI service at `/docs`. All AI routes
except `/health` require:

```text
X-Internal-Key: dev-internal-secret-change-me
```

AI routes include:

- `POST /predict`
- `POST /predict-batch`
- `GET /model/info`
- `GET /model/metrics`
- `POST /train`
- `GET /recommend/{student_id}`
- `GET /search?q=...`
- `GET /health`

Spring risk routes include:

- `POST /api/risk/run?term=N`
- `GET /api/risk`
- `GET /api/risk/model-info`
- `GET /api/risk/metrics`
- `POST /api/risk/train`
- recommendation and search proxy endpoints

Use the complete manual verification matrix in
[`docs/manual_test_checklist.md`](docs/manual_test_checklist.md). It covers
ER/EER design, keys and functional dependencies, normalization, DDL/DML,
joins, subqueries, aggregates, views, indexes, triggers, procedures,
transactions, ACID, concurrency, security/RBAC, CRUD, SQL traces, frontend
flow, ML preprocessing, model selection, training, evaluation, and AI
integration.

## Response envelope and errors

Successful application responses follow this structure:

```json
{
  "data": {},
  "trace": [
    {
      "sql": "SELECT ... WHERE id = ?",
      "params": ["123"],
      "rowsAffected": 1,
      "timeMs": 4
    }
  ]
}
```

Database rule violations are returned as clear client errors, including the
database message where appropriate. Risk operations return a graceful service
unavailable error when FastAPI is down; unrelated backend functionality
continues to work.

## Documentation

- [Project requirements](Education%20and%20Academics%20Management%20System%20Project%20Requirements.md)
- [Implementation plan](Implementation%20Plan%20Education%20and%20Academics%20Management%20System.md)
- [Manual test checklist](docs/manual_test_checklist.md)
- [Report outline](docs/report_outline.md)
- [Viva notes](docs/viva_notes.md)
- [ER/EER design notes](docs/01_er_eer_design.md)
- [Keys, FDs, and normalization](docs/02_keys_fd_normalization.md)
- [AI service README](ai-service/README.md)

## Validation policy

This coursework explicitly requires manual verification, so the repository
does not contain automated test files, test folders, or test dependencies.
Current validation consists of Docker health checks, frontend production
builds, backend compilation, Python syntax checks, API smoke checks, and the
manual checklist.

Known non-blocking development warnings may include:

- Vite's large JavaScript bundle warning.
- Flyway's warning when MySQL is newer than its tested support range.
- scikit-learn artifact serialization-version warnings when the training and
  runtime package versions differ.
