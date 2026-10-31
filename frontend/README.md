# frontend/

React + Vite single-page application.  
Only ever talks to the **Spring Boot backend** (`http://localhost:8080`) — never to the database or FastAPI directly.

## Stack

| Layer         | Library            | Version |
|---------------|--------------------|---------|
| Framework     | React              | 18.x    |
| Build tool    | Vite               | 5.x     |
| Routing       | React Router DOM   | 6.x     |
| HTTP client   | Axios              | 1.x     |
| UI components | Bootstrap 5        | 5.3.x   |
| Charts        | Recharts           | 2.x     |

> No test libraries are included (see project Rule 2).

## Prerequisites

- Node.js 18+ and npm 9+

## Setup

```bash
# From the frontend/ directory:
npm install
```

## Run (development)

```bash
npm run dev
# → http://localhost:5173
```

Vite proxies all `/api/*` requests to `http://localhost:8080` (see `vite.config.js`),  
so the Spring Boot backend must be running first.

## Demo credentials (seeded by V7__seed_users.sql + DataInitializer)

| Username    | Password     | Role       |
|-------------|--------------|------------|
| admin       | admin123     | Admin      |
| registrar1  | reg123       | Registrar  |
| faculty1    | faculty123   | Faculty    |
| student1    | student123   | Student    |

## File structure

```
src/
├── api/
│   └── axios.js          # Axios instance: JWT injection + { data, trace } unwrapping
├── auth/
│   ├── AuthContext.jsx   # JWT + user stored in localStorage
│   └── ProtectedRoute.jsx
├── components/
│   ├── Layout.jsx        # App shell (sidebar + topbar with Schema button)
│   ├── Sidebar.jsx       # Role-based nav (Admin / Registrar / Faculty / Student)
│   └── TracePanel.jsx    # SQL trace: flow diagram + SQL details table
├── pages/
│   ├── DashboardPage.jsx # Role-aware landing page with Recharts bar chart
│   ├── LoginPage.jsx     # Public login with animated blobs + trace panel
│   └── NotFoundPage.jsx
├── store/
│   ├── traceStore.js     # Module-level callback bridge (Axios ↔ React)
│   └── TraceContext.jsx  # Global React state for the latest SQL trace
├── App.jsx               # Router tree (all routes pre-declared as stubs)
├── index.css             # Design system: dark navy, indigo accent, glassmorphism
└── main.jsx
```

## Manual verification checklist

1. `npm run dev` starts without errors.
2. `/login` shows the split login page with animated blobs.
3. Log in as each of the 4 roles → navbar shows different menu items.
4. Click **View ER / Schema** in the topbar → navigates to `/schema` (stub).
5. Click **Run Health Check** on the dashboard → TracePanel expands showing `SELECT 1` SQL.
6. Switch tabs in TracePanel between **Flow Diagram** and **SQL Details**.
7. Log out → redirected to `/login`; browser back button does not re-enter protected area.
8. Open two tabs as different roles → each sees its own role-specific sidebar.
