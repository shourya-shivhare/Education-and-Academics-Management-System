import { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar';
import { useAuth } from '../auth/AuthContext';
import { useTrace } from '../store/TraceContext';

/**
 * Layout – application shell for all authenticated pages.
 *
 * Structure:
 *   <div.app-shell>
 *     <Sidebar />          (fixed 260px, role-based nav)
 *     <div.main-area>
 *       <header.topbar />  (sticky, 62px – user info, Schema button, logout)
 *       <main.page-content>
 *         <Outlet />       (current page component)
 *       </main>
 *     </div>
 *   </div>
 *
 * The "View ER / Schema" button lives here (in the topbar) as specified in
 * Prompt 16: visible on every protected page, navigates to /schema.
 */
export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { user, logout } = useAuth();
  const { trace, clearTrace } = useTrace();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const roleClass = user?.role?.toLowerCase() ?? 'student';

  return (
    <div className="app-shell">
      {/* ── Fixed sidebar ────────────────────────────────────────────── */}
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      {/* ── Scrollable main area ─────────────────────────────────────── */}
      <div className="main-area">
        {/* ── Sticky topbar ─────────────────────────────────────────── */}
        <header className="topbar">
          <div className="topbar-left">
            {/* Hamburger – mobile only */}
            <button
              id="menu-toggle-btn"
              className="menu-toggle"
              onClick={() => setSidebarOpen(prev => !prev)}
              aria-label="Toggle navigation"
            >
              ☰
            </button>
          </div>

          <div className="topbar-right">
            {/* ── "View ER / Schema" – Prompt 16 requirement ─────────── */}
            <button
              id="btn-view-schema"
              className="btn-ghost btn-schema"
              onClick={() => navigate('/schema')}
              title="View ER diagram and live schema explorer"
            >
              🗄️ View ER / Schema
            </button>

            {/* ── SQL trace indicator (appears when last response had a trace) */}
            {trace.length > 0 && (
              <button
                id="btn-clear-trace"
                className="btn-ghost btn-trace"
                onClick={clearTrace}
                title={`${trace.length} SQL queries traced – click to clear`}
              >
                🔍 {trace.length} SQL
              </button>
            )}

            <div className="topbar-divider" aria-hidden="true" />

            {/* ── User info + logout ─────────────────────────────────── */}
            <div className="topbar-user">
              <div className="avatar avatar-sm" aria-hidden="true">
                {user?.role?.[0] ?? '?'}
              </div>
              <span className="topbar-username">
                <span className={`role-tag ${roleClass}`} style={{ marginRight: 6 }}>
                  {user?.role}
                </span>
                {user?.personId ? `ID ${user.personId}` : `User #${user?.userId}`}
              </span>
              <button
                id="btn-logout"
                className="btn-logout"
                onClick={handleLogout}
                title="Sign out"
              >
                ⏻ Logout
              </button>
            </div>
          </div>
        </header>

        {/* ── Page content (rendered by React Router <Outlet>) ───────── */}
        <main className="page-content" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
