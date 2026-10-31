import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { TraceProvider } from './store/TraceContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import NotFoundPage from './pages/NotFoundPage';
// Prompt 17 pages
import StudentsPage   from './pages/StudentsPage';
import FacultyPage    from './pages/FacultyPage';
import SubjectsPage   from './pages/SubjectsPage';
import OfferingsPage  from './pages/OfferingsPage';
import EnrollmentPage from './pages/EnrollmentPage';
import AttendancePage from './pages/AttendancePage';
import MarksPage      from './pages/MarksPage';

/**
 * App – top-level router.
 *
 * Route structure:
 *   /login          – public (redirects home if already logged in)
 *   /*              – protected; wrapped in <Layout> (sidebar + topbar)
 *     /             – DashboardPage (role-aware)
 *     /students     – stub (Prompt 17)
 *     /faculty      – stub
 *     /subjects     – stub
 *     /offerings    – stub
 *     /enrollment   – stub
 *     /attendance   – stub
 *     /marks        – stub
 *     /transcript   – stub (student only)
 *     /reports      – stub (Prompt 18)
 *     /labs/acid    – stub (Prompt 18)
 *     /labs/concurrency – stub (Prompt 18)
 *     /schema       – stub (Prompt 18) – the "View ER / Schema" target
 *     /risk         – stub (Prompt 22)
 *     /console      – stub (ADMIN only, Prompt 18)
 */
export default function App() {
  return (
    <AuthProvider>
      <TraceProvider>
        <BrowserRouter>
          <Routes>
            {/* ── Public ─────────────────────────────────────────────────── */}
            <Route path="/login" element={<LoginPage />} />

            {/* ── Protected – any authenticated role ─────────────────────── */}
            <Route element={<ProtectedRoute />}>
              <Route element={<Layout />}>
                <Route index element={<DashboardPage />} />

                {/* Stubs – replaced one-by-one in Prompt 17 and 18 */}
                <Route path="/students"         element={<StudentsPage />} />
                <Route path="/faculty"          element={<FacultyPage />} />
                <Route path="/subjects"         element={<SubjectsPage />} />
                <Route path="/offerings"        element={<OfferingsPage />} />
                <Route path="/enrollment"       element={<EnrollmentPage />} />
                <Route path="/attendance"       element={<AttendancePage />} />
                <Route path="/marks"            element={<MarksPage />} />
                <Route path="/transcript"       element={<ComingSoon title="My Transcript" />} />
                <Route path="/reports"          element={<ComingSoon title="Reports" />} />
                <Route path="/labs/acid"        element={<ComingSoon title="ACID Lab" />} />
                <Route path="/labs/concurrency" element={<ComingSoon title="Concurrency Lab" />} />
                <Route path="/schema"           element={<ComingSoon title="Schema / ER Diagram" />} />
                <Route path="/risk"             element={<ComingSoon title="AI Risk Dashboard" />} />
                <Route path="/console"          element={<ComingSoon title="SQL Console (Admin)" />} />

                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </TraceProvider>
    </AuthProvider>
  );
}

/**
 * ComingSoon – placeholder for pages added in later prompts.
 * Shows the page title and which prompt implements it.
 */
function ComingSoon({ title }) {
  return (
    <div className="coming-soon">
      <div className="coming-soon-icon">🚧</div>
      <h2>{title}</h2>
      <p className="coming-soon-sub">This page will be implemented in the next prompt.</p>
      <p className="coming-soon-hint">
        The backend API is already ready — only the React UI is pending.
      </p>
    </div>
  );
}
