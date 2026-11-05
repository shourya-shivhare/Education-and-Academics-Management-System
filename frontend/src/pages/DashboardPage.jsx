import { useState, useEffect } from 'react';
import api from '../api/axios';
import { reportsApi } from '../api/entities';
import { useAuth } from '../auth/AuthContext';
import { useTrace } from '../store/TraceContext';
import TracePanel from '../components/TracePanel';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell,
} from 'recharts';

/**
 * DashboardPage – role-aware landing page.
 *
 * Calls GET /api/health on mount to:
 *   1. Verify the backend is reachable.
 *   2. Populate the TracePanel with a real SQL trace (SELECT 1).
 *
 * Also fetches a small stats snapshot so the cards show real numbers.
 * The bar chart uses Recharts – imported here to confirm the library works.
 */

const ROLE_META = {
  Admin: {
    greeting: 'System Administrator',
    color: '#ef4444',
    description:
      'Full system access. Manage all academic entities, run ACID and Concurrency labs, view the full schema and administer database roles.',
    stats: [
      { icon: '👥', label: 'Students',   path: '/api/students?size=1',   key: 'total' },
      { icon: '👨‍🏫', label: 'Faculty',    path: '/api/faculty?size=1',    key: 'total' },
      { icon: '📚', label: 'Subjects',   path: '/api/subjects?size=1',   key: 'total' },
      { icon: '📝', label: 'Offerings',  path: '/api/offerings?size=1',  key: 'total' },
    ],
  },
  Registrar: {
    greeting: 'Registrar',
    color: '#f59e0b',
    description:
      'Manage student records, enrollments and fee payments. Run reports and view the schema.',
    stats: [],
  },
  Faculty: {
    greeting: 'Faculty Member',
    color: '#3b82f6',
    description:
      'Record attendance and marks for your assigned course offerings. Run reports to monitor student performance.',
    stats: [],
  },
  Student: {
    greeting: 'Student',
    color: '#10b981',
    description:
      'View your enrollment status, attendance, marks and transcript. Check the Schema page to understand how your data is stored.',
    stats: [],
  },
};

const DBMS_CONCEPTS = [
  { icon: '✅', text: 'InnoDB Transactions & ACID properties'     },
  { icon: '✅', text: 'BEFORE/AFTER triggers (8 business rules)'   },
  { icon: '✅', text: 'Stored procedures with cursors & handlers'  },
  { icon: '✅', text: 'Views, security views & WITH CHECK OPTION'  },
  { icon: '✅', text: 'MySQL Roles & column-level GRANT'           },
  { icon: '✅', text: 'Flyway schema migrations (V + R scripts)'   },
  { icon: '✅', text: 'Optimistic locking on Marks (version col)'  },
  { icon: '✅', text: '22 labeled SQL queries (joins, CTEs, window)'},
  { icon: '✅', text: 'FULLTEXT index + TF-IDF subject search'     },
  { icon: '✅', text: 'Concurrency labs (deadlock, last-seat race)' },
];

/** Recharts custom tooltip */
function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'rgba(15,23,42,.95)',
      border: '1px solid rgba(99,102,241,.3)',
      borderRadius: 8,
      padding: '8px 14px',
      fontSize: 12,
      color: '#f1f5f9',
    }}>
      <strong>{label}</strong>
      <br />
      <span style={{ color: '#818cf8' }}>{payload[0].name}: </span>
      <strong>{payload[0].value}</strong>
    </div>
  );
}

/** Bar colours cycle */
const BAR_COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444'];

export default function DashboardPage() {
  const { user }          = useAuth();
  const { trace, clearTrace } = useTrace();
  const [health, setHealth]   = useState(null);
  const [busy,   setBusy]     = useState(false);
  const [chartData, setChartData] = useState([]);

  const meta = ROLE_META[user?.role] ?? ROLE_META.Student;

  // Ping /api/health on mount to populate the TracePanel
  useEffect(() => {
    (async () => {
      try {
        const { data } = await api.get('/health');
        setHealth(data);
      } catch {
        // backend might not be up yet – fail silently on dashboard
      }
      try {
        const rows = await reportsApi.departmentPerformance();
        const list = Array.isArray(rows) ? rows : [];
        setChartData(list.slice(0, 8).map(row => {
          const keys = Object.keys(row);
          const labelKey = keys.find(key => /department|name/i.test(key)) || keys[0];
          const valueKey = keys.find(key => /gpa|cgpa|average|performance|marks|score|percentage/i.test(key)) || keys[1];
          return { label: row[labelKey] || 'Department', value: Number(row[valueKey] || 0) };
        }));
      } catch {
        setChartData([]);
      }
    })();
  }, []);

  const handlePing = async () => {
    setBusy(true);
    clearTrace();
    try {
      const { data } = await api.get('/health');
      setHealth(data);
    } catch {
      // ignore
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      {/* ── Header ────────────────────────────────────────────────────── */}
      <div className="page-header">
        <div>
          <h1 className="page-title">
            {meta.greeting} Dashboard
          </h1>
          <p className="page-subtitle">
            Welcome, <strong>{user?.personId ? `Person #${user.personId}` : user?.role}</strong> 👋
            &nbsp;— EduManage is live and connected to MySQL.
          </p>
        </div>
        <span
          className="role-tag"
          style={{
            fontSize: 13,
            padding: '6px 16px',
            background: `${meta.color}22`,
            color: meta.color,
            borderRadius: 20,
            fontWeight: 700,
            border: `1px solid ${meta.color}44`,
            textTransform: 'uppercase',
            letterSpacing: '.8px',
          }}
        >
          {user?.role}
        </span>
      </div>

      {/* ── System stat cards ─────────────────────────────────────────── */}
      <div className="stats-row">
        <div className="stat-card stat-indigo">
          <span className="stat-icon">🗄️</span>
          <div>
            <span className="stat-label">Database</span>
            <span className="stat-value">MySQL 8 InnoDB</span>
          </div>
        </div>
        <div className="stat-card stat-amber">
          <span className="stat-icon">☕</span>
          <div>
            <span className="stat-label">Backend</span>
            <span className="stat-value">Spring Boot 3</span>
          </div>
        </div>
        <div
          className="stat-card stat-emerald"
          style={{ cursor: 'pointer' }}
          onClick={handlePing}
          title="Click to run health check and see SQL trace"
        >
          <span className="stat-icon">🔍</span>
          <div>
            <span className="stat-label">SQL Trace</span>
            <span className="stat-value">
              {health ? `Active · SELECT 1 ✓` : 'Click to ping'}
            </span>
          </div>
        </div>
        <div className="stat-card stat-sky">
          <span className="stat-icon">🏛️</span>
          <div>
            <span className="stat-label">Schema</span>
            <span className="stat-value">Flyway · 7 migrations</span>
          </div>
        </div>
      </div>

      {/* ── Main grid ─────────────────────────────────────────────────── */}
      <div className="dash-grid">

        {/* Role card */}
        <div className="dash-card">
          <h3>🔑 Your Role &amp; Permissions</h3>
          <div style={{ marginBottom: 14 }}>
            <span
              style={{
                display: 'inline-block',
                fontSize: 22,
                fontWeight: 800,
                letterSpacing: 2,
                textTransform: 'uppercase',
                color: meta.color,
                padding: '8px 18px',
                background: `${meta.color}18`,
                borderRadius: 8,
                border: `1px solid ${meta.color}33`,
              }}
            >
              {user?.role}
            </span>
          </div>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            {meta.description}
          </p>
          <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 10 }}>
            User ID: <code style={{ color: 'var(--accent-light)' }}>{user?.userId}</code>
            {user?.personId && (
              <>&nbsp;· Person ID: <code style={{ color: 'var(--accent-light)' }}>{user.personId}</code></>
            )}
          </p>
        </div>

        {/* DBMS concepts card */}
        <div className="dash-card">
          <h3>🏗️ DBMS Concepts Implemented</h3>
          <ul className="concept-list">
            {DBMS_CONCEPTS.map((c, i) => (
              <li key={i}>
                <span>{c.icon}</span>
                <span style={{ fontSize: 12.5 }}>{c.text}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* SQL trace demo card */}
        <div className="dash-card">
          <h3>🔍 Live SQL Trace Demo</h3>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 14 }}>
            Every API call records its SQL, bind parameters, rows affected and
            execution time. Expand the trace panel below to see the
            <strong> Front End → SQL Query → Database → Result</strong> flow.
          </p>
          <button
            id="btn-health-ping"
            className="btn-primary-custom"
            onClick={handlePing}
            disabled={busy}
          >
            {busy ? (
              <><span className="spinner" />Running…</>
            ) : (
              '▶ Run Health Check (GET /api/health)'
            )}
          </button>
          {health && (
            <div style={{
              marginTop: 12, fontSize: 12,
              color: 'var(--success)',
              background: 'rgba(16,185,129,.1)',
              padding: '8px 12px',
              borderRadius: 6,
              border: '1px solid rgba(16,185,129,.25)',
            }}>
              ✓ Backend reachable · {health.status ?? JSON.stringify(health)}
            </div>
          )}
        </div>

        {/* Enrollment trend chart (Recharts) */}
        <div className="dash-card" style={{ gridColumn: 'span 2' }}>
          <h3>📊 Department performance</h3>
          <p style={{ fontSize: 11.5, color: 'var(--text-muted)', marginBottom: 14 }}>
            Use Reports for live database-backed charts and parameterized query results.
          </p>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={chartData} margin={{ top: 4, right: 10, left: -10, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,.1)" />
              <XAxis
                dataKey="label"
                tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                axisLine={{ stroke: 'var(--border)' }}
                tickLine={false}
              />
              <YAxis
                tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                axisLine={{ stroke: 'var(--border)' }}
                tickLine={false}
              />
              <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(99,102,241,.06)' }} />
              <Bar dataKey="value" name="Performance" radius={[4, 4, 0, 0]}>
                {chartData.map((_, idx) => (
                  <Cell key={idx} fill={BAR_COLORS[idx % BAR_COLORS.length]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Quick nav card */}
        <div className="dash-card">
          <h3>⚡ Quick Navigation</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {[
              { label: '📈 Reports (22 SQL queries)',   path: '/reports'         },
              { label: '🗄️ Schema / ER Diagram',       path: '/schema'          },
              { label: '🧪 ACID Lab',                   path: '/labs/acid'       },
              { label: '⚙️ Concurrency Lab',            path: '/labs/concurrency'},
            ].map(item => (
              <a
                key={item.path}
                href={item.path}
                style={{
                  display: 'block',
                  padding: '9px 14px',
                  background: 'rgba(99,102,241,.06)',
                  border: '1px solid rgba(99,102,241,.15)',
                  borderRadius: 6,
                  color: 'var(--accent-light)',
                  fontSize: 13,
                  fontWeight: 500,
                  textDecoration: 'none',
                  transition: 'all .15s ease',
                }}
                onMouseEnter={e => { e.currentTarget.style.background = 'rgba(99,102,241,.14)'; }}
                onMouseLeave={e => { e.currentTarget.style.background = 'rgba(99,102,241,.06)'; }}
              >
                {item.label}
              </a>
            ))}
          </div>
        </div>

      </div>

      {/* ── SQL Trace Panel ────────────────────────────────────────────── */}
      <TracePanel trace={trace} title="Last Request SQL Trace" />
    </div>
  );
}
