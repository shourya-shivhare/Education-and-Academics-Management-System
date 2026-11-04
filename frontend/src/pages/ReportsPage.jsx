import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { reportsApi } from '../api/entities';
import { PageShell, ErrorAlert, InlineTrace, Input } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

function rowsFrom(data) { return Array.isArray(data) ? data : data?.content || []; }

export default function ReportsPage() {
  const [queries, setQueries] = useState([]);
  const [selected, setSelected] = useState(null);
  const [params, setParams] = useState([]);
  const [rows, setRows] = useState([]);
  const [trace, setTrace] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [topN, setTopN] = useState('3');
  const [chartRows, setChartRows] = useState([]);
  const [chartMode, setChartMode] = useState('departments');

  useEffect(() => {
    reportsApi.list().then(data => {
      setQueries(rowsFrom(data));
    }).catch(e => setError(e?.response?.data?.message || e.message));
    loadChart('departments');
  }, []);

  const loadChart = async (mode) => {
    setChartMode(mode);
    try {
      const data = mode === 'departments'
        ? await reportsApi.departmentPerformance()
        : await reportsApi.toppers(Number(topN) || 3);
      setChartRows(rowsFrom(data));
      setTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
  };

  const choose = query => {
    setSelected(query);
    setParams((query.params || []).map(p => p.default ?? ''));
    setRows([]);
    setError(null);
  };

  const run = async () => {
    if (!selected) return;
    setLoading(true); setError(null);
    try {
      setRows(rowsFrom(await reportsApi.run(selected.id, params)));
      setTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  };

  const download = async () => {
    if (!selected) return;
    try {
      const response = await reportsApi.csv(selected.id, params);
      const url = URL.createObjectURL(response);
      const link = document.createElement('a');
      link.href = url; link.download = `${selected.id}.csv`; link.click();
      URL.revokeObjectURL(url);
    } catch (e) { setError(e?.response?.data?.message || e.message); }
  };

  const columns = useMemo(() => rows[0] ? Object.keys(rows[0]) : [], [rows]);
  const chartKey = chartMode === 'departments'
    ? (chartRows[0] && (Object.keys(chartRows[0]).find(k => /department|name/i.test(k)) || Object.keys(chartRows[0])[0]))
    : (chartRows[0] && (Object.keys(chartRows[0]).find(k => /student|name|roll/i.test(k)) || Object.keys(chartRows[0])[0]));
  const valueKey = chartRows[0] && (Object.keys(chartRows[0]).find(k => /gpa|cgpa|average|performance|marks|score|percentage/i.test(k)) || Object.keys(chartRows[0])[1]);

  return <PageShell title="Reports" subtitle="Run the 22 parameterized SQL reports and inspect their traces">
    <ErrorAlert message={error} onClose={() => setError(null)} />
    <div className="report-layout">
      <section className="dash-card">
        <h3>Query catalogue</h3>
        <div className="report-query-list">
          {queries.map(q => <button key={q.id} className={`btn btn-sm ${selected?.id === q.id ? 'btn-primary' : 'btn-outline'}`} onClick={() => choose(q)}>
            {q.id} · {q.title}
          </button>)}
        </div>
      </section>
      <section className="dash-card">
        <h3>{selected ? `${selected.id} · ${selected.title}` : 'Select a query'}</h3>
        {selected && <>
          <p className="form-hint">{selected.description}</p>
          {(selected.params || []).map((p, i) => <label className="form-field" key={p.name}>
            <span className="field-label">{p.name}</span>
            <Input type={p.type === 'INTEGER' ? 'number' : p.type === 'DECIMAL' ? 'number' : 'text'} value={params[i] ?? ''} onChange={v => setParams(xs => xs.map((x, j) => j === i ? v : x))} placeholder={p.description} />
          </label>)}
          <div className="entity-actions"><button className="btn btn-primary" onClick={run} disabled={loading}>Run query</button><button className="btn btn-outline" onClick={download}>Export CSV</button></div>
          <pre className="sql-preview">{selected.sql}</pre>
        </>}
      </section>
    </div>
    {rows.length > 0 && <section className="dash-card report-results"><h3>Result ({rows.length} rows)</h3><div className="table-wrapper"><table className="entity-table"><thead><tr>{columns.map(c => <th key={c}>{c}</th>)}</tr></thead><tbody>{rows.map((r, i) => <tr key={i}>{columns.map(c => <td key={c}>{String(r[c] ?? '—')}</td>)}</tr>)}</tbody></table></div></section>}
    <section className="dash-card">
      <div className="entity-header"><div><h3>{chartMode === 'departments' ? 'Department performance' : 'Department toppers'}</h3></div><div className="entity-actions"><button className="btn btn-outline" onClick={() => loadChart('departments')}>Performance</button><Input type="number" min="1" value={topN} onChange={setTopN} /><button className="btn btn-outline" onClick={() => loadChart('toppers')}>Toppers</button></div></div>
      {chartRows.length > 0 && <ResponsiveContainer width="100%" height={280}><BarChart data={chartRows}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey={chartKey} hide /><YAxis /><Tooltip /><Bar dataKey={valueKey} fill="#6366f1" /></BarChart></ResponsiveContainer>}
    </section>
    {trace.length > 0 && <InlineTrace trace={trace} />}
  </PageShell>;
}
