# Education and Academics Management System

A college management system for departments, programs, courses, students, faculty, enrollments, grades, and attendance. **This is college DBMS coursework.**

The **MySQL 8 (InnoDB) database is the source of truth** — all business rules (constraints, triggers, stored procedures, views, and roles) live in the database. Application code only calls those procedures and queries; it does not encode business logic.

## Architecture

```
                       +-----------------+
   browser  <--------> |  Spring Boot 3  |  <-- Flyway runs migrations as flyway_user
  (React/Vite)         |  (JdbcTemplate) |  <-- app calls stored procs/queries as app_user
                       +--------+--------+
                                |
              +-----------------+-----------------+
              |                                   |
       +------+------+                      +------+------+
       |  MySQL 8    |                      |  FastAPI    |  internal only
       |  (InnoDB)   |                      |  (ML svc)   |  (X-Internal-Key)
       | source of   |                      +-------------+  never browser-facing
       |   truth     |
       +-------------+
```

- **MySQL 8 (InnoDB)** is the source of truth. Business rules live in the DB: constraints, triggers, stored procedures, views, and roles. Java only calls them.
- **Spring Boot 3** on Java 17+ uses Spring `JdbcTemplate` with hand-written SQL. **NO JPA/Hibernate.** Flyway (`flyway-core` + `flyway-mysql`) manages all schema changes. `ddl-auto` is **not** used.
- **React (Vite)** is the frontend. The browser only ever talks to Spring Boot.
- **Python FastAPI** service for ML features is **internal only** — never exposed to the browser. The Spring Boot backend calls it with an `X-Internal-Key` header.
- Every API response uses the envelope: `{ data, trace: [ { sql, params, rowsAffected, timeMs } ] }`.

## Prerequisites

| Tool       | Version   | Notes                                            |
|------------|-----------|--------------------------------------------------|
| JDK        | 17+       | Backend (Spring Boot 3)                          |
| Maven      | 3.8+      | Backend build tool                               |
| Node.js    | 18+       | Frontend (Vite)                                  |
| Python     | 3.10+     | AI service (FastAPI)                             |
| MySQL      | 8.0+      | InnoDB; the primary DBMS                         |

## Project Structure

```
Education-and-Academics-Management-System/
├── backend/            # Spring Boot 3 / Java 17, Maven, JdbcTemplate + Flyway (populated later)
├── frontend/           # React + Vite (populated later)
├── ai-service/         # Python FastAPI, internal ML; requires X-Internal-Key (populated later)
├── database/
│   ├── setup/          # DBA-run pre-Flyway bootstrap scripts (create database + users)
│   ├── seed/           # seed data SQL (later)
│   ├── queries/        # reference / example SQL queries (later)
│   └── demo/           # the single allowed SQL-injection demo file (later)
├── docs/               # project documentation (later)
├── README.md
└── .gitignore
```

## Run Instructions

### 1. Database (run FIRST, as MySQL admin/root)

```bash
# Bootstrap the database and users before starting Flyway
mysql -u root -p < database/setup/00_create_database_and_users.sql
```

Flyway migrations run automatically from the backend at startup (as `flyway_user`) and create tables, triggers, procedures, views, and the `app_user` grants/roles.

### 2. Backend (Spring Boot, port 8080)

```bash
cd backend
mvn spring-boot:run
```

### 3. Frontend (React + Vite, port 5173)

```bash
cd frontend
npm install
npm run dev
```

### 4. AI Service (FastAPI, port 8000 — internal only)

```bash
cd ai-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

## API Response Envelope

Every API response is wrapped in a standard envelope that includes a SQL trace for manual verification and debugging:

```json
{
  "data": {},
  "trace": [
    { "sql": "SELECT ... WHERE id = ?", "params": ["123"], "rowsAffected": 1, "timeMs": 4 }
  ]
}
```

## Testing

Testing is **manual only**. There are no automated test suites, no test folders, and no test dependencies in this project. Verify changes by running the stack and checking the `trace` in each API response.

## Documentation

- [Project Requirements](./Education%20and%20Academics%20Management%20System%20Project%20Requirements.md)
- [Implementation Plan](./Implementation%20Plan%20Education%20and%20Academics%20Management%20System.md)
