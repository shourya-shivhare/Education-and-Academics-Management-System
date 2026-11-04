import { useState } from 'react';
import { consoleApi } from '../api/entities';
import { PageShell, ErrorAlert, InlineTrace, Input } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

export default function ConsolePage() {
  const [sql, setSql] = useState('SELECT * FROM department LIMIT 20');
  const [params, setParams] = useState('');
  const [rows, setRows] = useState([]);
  const [trace, setTrace] = useState([]);
  const [error, setError] = useState(null);
  const run = async () => { try { setRows(await consoleApi.select(sql, params ? params.split(',').map(x => x.trim()) : [])); setTrace(getLatestTrace()); setError(null); } catch (e) { setError(e?.response?.data?.message || e.message); } };
  const columns = rows[0] ? Object.keys(rows[0]) : [];
  return <PageShell title="SQL Console" subtitle="Admin-only parameterized SELECT console">
    <ErrorAlert message={error} onClose={() => setError(null)} />
    <div className="dash-card"><label className="field-label">SELECT statement</label><textarea className="form-control sql-editor" rows={6} value={sql} onChange={e => setSql(e.target.value)} /><Input value={params} onChange={setParams} placeholder="Comma-separated bind parameters" /><button className="btn btn-primary" onClick={run}>Execute SELECT</button></div>
    {rows.length > 0 && <div className="table-wrapper"><table className="entity-table"><thead><tr>{columns.map(c => <th key={c}>{c}</th>)}</tr></thead><tbody>{rows.map((r, i) => <tr key={i}>{columns.map(c => <td key={c}>{String(r[c] ?? '—')}</td>)}</tr>)}</tbody></table></div>}
    {trace.length > 0 && <InlineTrace trace={trace} />}
  </PageShell>;
}
