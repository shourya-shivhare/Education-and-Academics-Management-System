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
import ReportsPage from './pages/ReportsPage';
import SchemaPage from './pages/SchemaPage';
import LabPage from './pages/LabPage';
import ConsolePage from './pages/ConsolePage';
import RiskPage from './pages/RiskPage';
import TranscriptPage from './pages/TranscriptPage';

/**
 * App – top-level router.
 *
 * Route structure:
 *   /login          – public (redirects home if already logged in)
 *   /*              – protected; wrapped in <Layout> (sidebar + topbar)
 *     /             – DashboardPage (role-aware)
 *     /students … /transcript – protected academic pages
 *     /reports, /labs, /schema, /risk, /console – protected DBMS/analytics pages
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

                <Route path="/students"         element={<StudentsPage />} />
                <Route path="/faculty"          element={<FacultyPage />} />
                <Route path="/subjects"         element={<SubjectsPage />} />
                <Route path="/offerings"        element={<OfferingsPage />} />
                <Route path="/enrollment"       element={<EnrollmentPage />} />
                <Route path="/attendance"       element={<AttendancePage />} />
                <Route path="/marks"            element={<MarksPage />} />
                <Route path="/transcript"       element={<TranscriptPage />} />
                <Route path="/reports"          element={<ReportsPage />} />
                <Route path="/labs/acid"        element={<LabPage kind="acid" />} />
                <Route path="/labs/concurrency" element={<LabPage kind="concurrency" />} />
                <Route path="/schema"           element={<SchemaPage />} />
                <Route path="/risk"             element={<RiskPage />} />
                <Route path="/console"          element={<ConsolePage />} />

                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </TraceProvider>
    </AuthProvider>
  );
}
