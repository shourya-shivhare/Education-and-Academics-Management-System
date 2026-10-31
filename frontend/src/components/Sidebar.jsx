import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * Sidebar – role-based navigation panel.
 *
 * Role → menu mapping follows the RBAC matrix defined in V6__roles_and_grants.sql:
 *   Admin      – full access to all pages
 *   Registrar  – students, enrollment, reports
 *   Faculty    – own offerings, attendance entry, marks entry, reports
 *   Student    – own enrollment, attendance, marks, transcript
 *
 * Every role sees the "Schema / ER" entry (Prompt 16 requirement).
 * Entries for pages not yet built render as stubs (ComingSoon) in App.jsx.
 */

const NAV_CONFIG = {
  Admin: [
    { section: 'Academic' },
    { icon: '👥', label: 'Students',          path: '/students'    },
    { icon: '👨‍🏫', label: 'Faculty',           path: '/faculty'     },
    { icon: '📚', label: 'Subjects',           path: '/subjects'    },
    { icon: '📅', label: 'Course Offerings',   path: '/offerings'   },
    { icon: '📝', label: 'Enrollment',         path: '/enrollment'  },
    { icon: '📊', label: 'Attendance',         path: '/attendance'  },
    { icon: '✏️', label: 'Marks',              path: '/marks'       },
    { section: 'Analytics' },
    { icon: '📈', label: 'Reports (22 SQL)',   path: '/reports'     },
    { icon: '🧪', label: 'ACID Lab',           path: '/labs/acid'         },
    { icon: '⚙️', label: 'Concurrency Lab',   path: '/labs/concurrency'  },
    { section: 'System' },
    { icon: '🗄️', label: 'Schema / ER',       path: '/schema'      },
    { icon: '🤖', label: 'AI Risk Dashboard', path: '/risk'        },
    { icon: '💻', label: 'SQL Console',        path: '/console'     },
  ],
  Registrar: [
    { section: 'Academic' },
    { icon: '👥', label: 'Students',    path: '/students'    },
    { icon: '📝', label: 'Enrollment',  path: '/enrollment'  },
    { section: 'Analytics' },
    { icon: '📈', label: 'Reports',     path: '/reports'     },
    { section: 'System' },
    { icon: '🗄️', label: 'Schema / ER', path: '/schema'     },
  ],
  Faculty: [
    { section: 'Teaching' },
    { icon: '📅', label: 'My Offerings', path: '/offerings'  },
    { icon: '📊', label: 'Attendance',   path: '/attendance' },
    { icon: '✏️', label: 'Marks',        path: '/marks'      },
    { section: 'Analytics' },
    { icon: '📈', label: 'Reports',      path: '/reports'    },
    { section: 'System' },
    { icon: '🗄️', label: 'Schema / ER', path: '/schema'     },
  ],
  Student: [
    { section: 'My Academics' },
    { icon: '📋', label: 'My Enrollment',  path: '/enrollment'  },
    { icon: '📊', label: 'My Attendance',  path: '/attendance'  },
    { icon: '✏️', label: 'My Marks',       path: '/marks'       },
    { icon: '📜', label: 'Transcript',     path: '/transcript'  },
    { section: 'System' },
    { icon: '🗄️', label: 'Schema / ER',   path: '/schema'      },
  ],
};

/**
 * @param {{ isOpen: boolean, onClose: () => void }} props
 */
export default function Sidebar({ isOpen, onClose }) {
  const { user } = useAuth();
  const items = NAV_CONFIG[user?.role] ?? [];

  const navCls = ({ isActive }) =>
    'nav-link-item' + (isActive ? ' active-nav' : '');

  return (
    <>
      {/* Semi-transparent overlay on mobile – tap to close */}
      <div
        className={`sidebar-overlay ${isOpen ? 'visible' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className={`sidebar ${isOpen ? 'open' : ''}`} aria-label="Main navigation">
        {/* ── Brand ─────────────────────────────────────────────────── */}
        <NavLink to="/" className="sidebar-brand" onClick={onClose}>
          <span className="brand-emoji">🎓</span>
          <span>
            <span className="brand-text-name">EduManage</span>
            <span className="brand-text-sub">DBMS Coursework</span>
          </span>
        </NavLink>

        {/* ── Navigation ────────────────────────────────────────────── */}
        <nav className="sidebar-nav" aria-label="Role navigation">
          {/* Dashboard – always first for every role */}
          <NavLink to="/" end className={navCls} onClick={onClose}>
            <span className="nav-icon">🏠</span>
            Dashboard
          </NavLink>

          {items.map((item, idx) =>
            item.section ? (
              <span key={`sec-${idx}`} className="nav-section-label">
                {item.section}
              </span>
            ) : (
              <NavLink
                key={item.path}
                to={item.path}
                className={navCls}
                onClick={onClose}
              >
                <span className="nav-icon">{item.icon}</span>
                {item.label}
              </NavLink>
            )
          )}
        </nav>

        {/* ── Footer – current user info ──────────────────────────── */}
        <div className="sidebar-footer">
          <div className="sb-user">
            <div className="avatar avatar-md" aria-hidden="true">
              {user?.role?.[0] ?? '?'}
            </div>
            <div className="sb-user-info">
              <span className="sb-user-name">
                {user?.personId
                  ? `Person #${user.personId}`
                  : `User #${user?.userId}`}
              </span>
              <span className={`role-tag ${user?.role?.toLowerCase()}`}>
                {user?.role}
              </span>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
