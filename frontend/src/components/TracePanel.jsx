import { useState } from 'react';
import { useTrace } from '../store/TraceContext';

/**
 * TracePanel – visualises the SQL trace from the last API response.
 *
 * DBMS concept: every Spring Boot response includes a `trace` array showing
 * each parameterized SQL statement, its bind parameters, rows affected and
 * execution time in ms.  This panel makes that trace visible to the user,
 * demonstrating the Front End → SQL → Database → Result flow.
 *
 * Props:
 *   trace  {object[]} – override; if omitted, reads from global TraceContext.
 *   title  {string}   – optional header label (default "SQL Trace").
 *
 * Each trace entry (SqlTraceEntry.java):
 *   { sql: string, params: object|any[], rowsAffected: number, timeMs: number }
 */

/** Flow diagram node definitions */
const FLOW = [
  { type: 'node', icon: '🌐', label: 'Browser',      sub: 'React + Axios'              },
  { type: 'arrow', dir: 'out',  label: 'HTTP Request'                                   },
  { type: 'node', icon: '☕', label: 'Spring Boot',   sub: 'Controller → Service → Repo' },
  { type: 'arrow', dir: 'db',   label: 'SQL Query'                                      },
  { type: 'node', icon: '🗄️', label: 'MySQL',         sub: 'InnoDB Engine'              },
  { type: 'arrow', dir: 'in',   label: 'ResultSet'                                      },
  { type: 'node', icon: '☕', label: 'Spring Boot',   sub: 'Trace + Envelope'            },
  { type: 'arrow', dir: 'in',   label: 'JSON Response'                                  },
  { type: 'node', icon: '🌐', label: 'Browser',       sub: 'Unwrapped data'             },
];

function timePillClass(ms) {
  if (ms <= 30)  return 'time-pill time-fast';
  if (ms <= 100) return 'time-pill time-medium';
  return 'time-pill time-slow';
}

function formatParams(params) {
  if (params == null) return null;
  if (Array.isArray(params) && params.length === 0) return null;
  if (typeof params === 'object' && !Array.isArray(params) && Object.keys(params).length === 0) return null;
  return JSON.stringify(params);
}

export default function TracePanel({ trace: propTrace, title = 'SQL Trace' }) {
  const { trace: globalTrace } = useTrace();
  const [collapsed, setCollapsed] = useState(true);
  const [activeTab, setActiveTab] = useState('flow'); // 'flow' | 'table'

  // Prefer explicitly passed trace, fall back to global context
  const trace = propTrace ?? globalTrace;

  if (!trace || trace.length === 0) return null;

  const totalTimeMs = trace.reduce((s, e) => s + (e.timeMs ?? 0), 0);
  const totalRows   = trace.reduce((s, e) => s + (e.rowsAffected ?? 0), 0);

  return (
    <div className="trace-panel" id="trace-panel">
      {/* ── Header / toggle ─────────────────────────────────────────── */}
      <button
        className="trace-toggle"
        onClick={() => setCollapsed(c => !c)}
        aria-expanded={!collapsed}
        aria-controls="trace-body"
      >
        <span className="trace-toggle-left">
          <span aria-hidden="true">🔍</span>
          <strong>{title}</strong>
          <span className="trace-toggle-badges">
            <span className="trace-badge tb-queries">
              {trace.length} {trace.length === 1 ? 'query' : 'queries'}
            </span>
            <span className="trace-badge tb-time">{totalTimeMs} ms total</span>
            {totalRows > 0 && (
              <span className="trace-badge tb-rows">{totalRows} rows</span>
            )}
          </span>
        </span>
        <span className="trace-chevron" aria-hidden="true">
          {collapsed ? '▼ expand' : '▲ collapse'}
        </span>
      </button>

      {/* ── Body ────────────────────────────────────────────────────── */}
      {!collapsed && (
        <div className="trace-body" id="trace-body">
          {/* Tab switcher */}
          <div className="trace-tabs" role="tablist">
            <button
              role="tab"
              id="tab-flow"
              aria-selected={activeTab === 'flow'}
              className={`trace-tab ${activeTab === 'flow' ? 'active' : ''}`}
              onClick={() => setActiveTab('flow')}
            >
              🔀 Flow Diagram
            </button>
            <button
              role="tab"
              id="tab-sql"
              aria-selected={activeTab === 'table'}
              className={`trace-tab ${activeTab === 'table' ? 'active' : ''}`}
              onClick={() => setActiveTab('table')}
            >
              📋 SQL Details
            </button>
          </div>

          {/* ── Flow diagram ──────────────────────────────────────── */}
          {activeTab === 'flow' && (
            <div className="trace-flow" role="img" aria-label="SQL request flow diagram">
              {FLOW.map((step, i) => {
                if (step.type === 'node') {
                  return (
                    <div key={i} className="flow-node">
                      <div className="flow-node-icon">{step.icon}</div>
                      <div className="flow-node-label">{step.label}</div>
                      <div className="flow-node-sub">{step.sub}</div>
                    </div>
                  );
                }
                // Arrow connector
                return (
                  <div key={i} className={`flow-connector flow-${step.dir}`}>
                    <div className="flow-arrow-line" />
                    <div className="flow-arrow-label">{step.label}</div>
                  </div>
                );
              })}
            </div>
          )}

          {/* ── SQL details table ────────────────────────────────── */}
          {activeTab === 'table' && (
            <div className="trace-table-wrap">
              <table className="trace-tbl" aria-label="SQL trace details">
                <thead>
                  <tr>
                    <th className="col-num">#</th>
                    <th>SQL Statement</th>
                    <th>Parameters</th>
                    <th>Rows Affected</th>
                    <th>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {trace.map((entry, i) => {
                    const params = formatParams(entry.params);
                    return (
                      <tr key={i}>
                        <td className="col-num">{i + 1}</td>
                        <td className="col-sql">
                          <code>{entry.sql}</code>
                        </td>
                        <td className="col-params">
                          {params
                            ? <code>{params}</code>
                            : <span style={{ color: 'var(--text-muted)' }}>—</span>}
                        </td>
                        <td>
                          {entry.rowsAffected != null
                            ? entry.rowsAffected
                            : <span style={{ color: 'var(--text-muted)' }}>—</span>}
                        </td>
                        <td>
                          <span className={timePillClass(entry.timeMs)}>
                            {entry.timeMs} ms
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr>
                    <td />
                    <td />
                    <td className="total-label">Total</td>
                    <td><strong>{totalRows}</strong></td>
                    <td>
                      <span className={timePillClass(totalTimeMs)}>
                        {totalTimeMs} ms
                      </span>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
