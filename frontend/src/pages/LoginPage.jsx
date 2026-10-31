import { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../auth/AuthContext';
import { useTrace } from '../store/TraceContext';
import TracePanel from '../components/TracePanel';

/**
 * LoginPage – public entry point.
 *
 * POST /api/auth/login  →  { data: TokenResponse, trace: [...] }
 *
 * After the Axios interceptor unwraps the envelope:
 *   response.data = { token, type, userId, personId, role }
 *   trace         = [{ sql, params, rowsAffected, timeMs }, ...]
 *
 * The TracePanel below the form shows the SELECT + UPDATE queries that the
 * backend ran (user lookup + last_login stamp), demonstrating parameterized
 * SQL even on the authentication path.
 */
export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  const { login, isAuthenticated } = useAuth();
  const { trace }                  = useTrace();
  const navigate                   = useNavigate();
  const location                   = useLocation();

  // Already authenticated → skip the login page
  const from = location.state?.from?.pathname ?? '/';
  if (isAuthenticated) return <Navigate to={from} replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // POST /api/auth/login – interceptor unwraps envelope → response.data = TokenResponse
      const { data } = await api.post('/auth/login', { username, password });
      login(data);                       // persist token + user info
      navigate(from, { replace: true }); // redirect back to the attempted page
    } catch (err) {
      // error envelope: { code, message, trace }
      const msg = err.response?.data?.message ?? 'Invalid username or password.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* ── Animated gradient background ─────────────────────────────── */}
      <div className="login-blobs" aria-hidden="true">
        <div className="blob blob-1" />
        <div className="blob blob-2" />
        <div className="blob blob-3" />
      </div>

      <div className="login-container">
        {/* ── Left info panel ────────────────────────────────────────── */}
        <div className="login-left">
          <div className="ll-brand">
            <span className="ll-brand-emoji">🎓</span>
            <h1>EduManage</h1>
            <p>Education and Academics Management System</p>
          </div>

          <div className="ll-features">
            <div className="ll-feature">
              <span className="ll-feature-icon">🗄️</span>
              <span>MySQL 8 with InnoDB triggers, procedures &amp; views</span>
            </div>
            <div className="ll-feature">
              <span className="ll-feature-icon">☕</span>
              <span>Spring Boot 3 — JdbcTemplate + Flyway migrations</span>
            </div>
            <div className="ll-feature">
              <span className="ll-feature-icon">🔍</span>
              <span>Real-time SQL trace on every API response</span>
            </div>
            <div className="ll-feature">
              <span className="ll-feature-icon">🧪</span>
              <span>ACID &amp; Concurrency labs with live demos</span>
            </div>
            <div className="ll-feature">
              <span className="ll-feature-icon">🤖</span>
              <span>ML-powered student at-risk prediction (FastAPI)</span>
            </div>
          </div>

          {/* Demo credentials (from V7__seed_users.sql + DataInitializer) */}
          <div className="ll-creds">
            <p className="ll-creds-title">Demo credentials</p>
            <div className="cred-grid">
              <div className="cred-item">
                <span className="cred-role">Admin</span>
                admin / admin123
              </div>
              <div className="cred-item">
                <span className="cred-role">Registrar</span>
                registrar1 / reg123
              </div>
              <div className="cred-item">
                <span className="cred-role">Faculty</span>
                faculty1 / faculty123
              </div>
              <div className="cred-item">
                <span className="cred-role">Student</span>
                student1 / student123
              </div>
            </div>
          </div>
        </div>

        {/* ── Right form panel ───────────────────────────────────────── */}
        <div className="login-right">
          <div>
            <h2 className="login-form-title">Welcome back</h2>
            <p className="login-form-sub">Sign in to continue to EduManage</p>

            <form onSubmit={handleSubmit} className="login-form" noValidate>
              <div className="lf-group">
                <label className="lf-label" htmlFor="login-username">
                  Username
                </label>
                <input
                  id="login-username"
                  type="text"
                  className="lf-input"
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  placeholder="e.g. admin"
                  required
                  autoComplete="username"
                  autoFocus
                />
              </div>

              <div className="lf-group">
                <label className="lf-label" htmlFor="login-password">
                  Password
                </label>
                <input
                  id="login-password"
                  type="password"
                  className="lf-input"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  required
                  autoComplete="current-password"
                />
              </div>

              {error && (
                <div className="lf-error" role="alert">
                  <span aria-hidden="true">⚠️</span>
                  {error}
                </div>
              )}

              <button
                id="btn-login-submit"
                type="submit"
                className="btn-login"
                disabled={loading || !username || !password}
              >
                {loading ? (
                  <>
                    <span className="spinner" aria-hidden="true" />
                    Signing in…
                  </>
                ) : (
                  'Sign In →'
                )}
              </button>
            </form>

            {/*
              SQL Trace panel – shown after a login attempt (success or failure).
              Demonstrates that the auth SELECT uses a parameterized query (? placeholder)
              so the username bind value appears separately in the trace.
            */}
            {trace.length > 0 && (
              <TracePanel trace={trace} title="Authentication SQL Trace" />
            )}
          </div>

          <p className="login-footer">
            DBMS Coursework · MySQL 8 · Spring Boot 3 · React (Vite)
          </p>
        </div>
      </div>
    </div>
  );
}
