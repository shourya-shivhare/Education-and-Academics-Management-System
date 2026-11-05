import { useCallback, useEffect, useMemo, useState } from 'react';
import { riskApi } from '../api/entities';
import { ErrorAlert, InlineTrace, PageShell, Input, Select } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';
import { useAuth } from '../auth/AuthContext';

function RiskBadge({ value }) {
  const level = String(value || 'Low').toLowerCase();
  return <span className={`badge badge-${level === 'high' ? 'dropped' : level === 'medium' ? 'pending' : 'active'}`}>{value}</span>;
}

function MetricBars({ metrics }) {
  const keys = ['accuracy', 'precision', 'recall', 'f1', 'roc_auc', 'pr_auc'];
  return <div className="form-grid">{keys.map(key => <div key={key}>
    <strong>{key.replace('_', ' ').toUpperCase()}</strong>
    <div style={{ background: '#e5e7eb', borderRadius: 6, height: 12, marginTop: 5 }}>
      <div style={{ width: `${Math.min(100, Number(metrics?.[key] || 0) * 100)}%`, background: '#2563eb', height: '100%', borderRadius: 6 }} />
    </div>
    <small>{metrics?.[key] == null ? '—' : Number(metrics[key]).toFixed(3)}</small>
  </div>)}</div>;
}

export default function RiskPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [term, setTerm] = useState('');
  const [level, setLevel] = useState('');
  const [offering, setOffering] = useState('');
  const [sortDesc, setSortDesc] = useState(true);
  const [whatIf, setWhatIf] = useState({ studentId: '', termId: '', attendance_pct_midterm: 75, internal1_avg_pct: 65, internal2_avg_pct: 65 });
  const [prediction, setPrediction] = useState(null);
  const [model, setModel] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const data = await riskApi.list({
        term: term || undefined, level: level || undefined, offeringId: offering || undefined,
      });
      setRows(Array.isArray(data) ? data : []);
      setTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [term, level, offering]);

  useEffect(() => { load(); }, []);
  const sorted = useMemo(() => [...rows].sort((a, b) => sortDesc
    ? Number(b.risk_score || 0) - Number(a.risk_score || 0)
    : Number(a.risk_score || 0) - Number(b.risk_score || 0)), [rows, sortDesc]);

  const run = async () => {
    if (!term) return;
    setLoading(true); setError(null);
    try { await riskApi.run(term); setTrace(getLatestTrace()); await load(); }
    catch (e) { setError(e?.response?.data?.message || e.message); setTrace(getLatestTrace()); }
    finally { setLoading(false); }
  };
  const loadInsights = async () => {
    try {
      const [info, values] = await Promise.all([riskApi.modelInfo(), riskApi.metrics()]);
      setModel(info); setMetrics(values);
    } catch (e) { setError(e?.response?.data?.message || e.message); }
  };
  const predict = async () => {
    try { setPrediction(await riskApi.predict({
      studentId: Number(whatIf.studentId), termId: Number(whatIf.termId),
      features: {
        attendance_pct_midterm: Number(whatIf.attendance_pct_midterm),
        internal1_avg_pct: Number(whatIf.internal1_avg_pct),
        internal2_avg_pct: Number(whatIf.internal2_avg_pct),
      },
    })); setTrace(getLatestTrace()); }
    catch (e) { setError(e?.response?.data?.message || e.message); }
  };
  const loadRecommendations = async (studentId) => {
    try { setRecommendations(await riskApi.recommend(Number(studentId))); }
    catch (e) { setError(e?.response?.data?.message || e.message); }
  };

  return <PageShell title="At-Risk Dashboard" subtitle="Mid-term predictions with database traceability">
    <ErrorAlert message={error} onClose={() => setError(null)} />
    <section className="dash-card">
      <div className="form-row">
        <label className="form-field"><span className="field-label">Term ID</span><Input type="number" value={term} onChange={setTerm} /></label>
        <label className="form-field"><span className="field-label">Offering ID</span><Input type="number" value={offering} onChange={setOffering} /></label>
        <label className="form-field"><span className="field-label">Risk level</span><Select value={level} onChange={setLevel} options={[{ value: '', label: 'All' }, { value: 'High', label: 'High' }, { value: 'Medium', label: 'Medium' }, { value: 'Low', label: 'Low' }]} placeholder={null} /></label>
      </div>
      <button className="btn btn-primary" onClick={run} disabled={loading || !term}>Run Risk Analysis</button>{' '}
      <button className="btn btn-ghost" onClick={load} disabled={loading}>Refresh</button>{' '}
      <button className="btn btn-ghost" onClick={loadInsights}>Model Insights</button>
    </section>
    <section className="dash-card">
      <h3>Risk results <button className="btn btn-ghost" onClick={() => setSortDesc(v => !v)}>Sort score {sortDesc ? '↓' : '↑'}</button></h3>
      <div className="table-wrapper"><table className="entity-table"><thead><tr><th>Student</th><th>Term</th><th>Score</th><th>Level</th><th>Model</th><th>Factors</th><th>Recommendations</th></tr></thead><tbody>
        {sorted.map(row => <tr key={row.risk_id}><td>{row.student_id} {row.first_name} {row.last_name}</td><td>{row.term_id}</td><td>{Number(row.risk_score).toFixed(1)}%</td><td><RiskBadge value={row.risk_label} /></td><td>{row.model_version || '—'}</td><td>{String(row.contributing_factors || '—').slice(0, 100)}</td><td><button className="btn btn-ghost" onClick={() => { setWhatIf(v => ({ ...v, studentId: row.student_id, termId: row.term_id })); loadRecommendations(row.student_id); }}>View</button></td></tr>)}
      </tbody></table></div>
      {!sorted.length && <p className="form-hint">No saved risk results match these filters.</p>}
    </section>
    {trace.length > 0 && <InlineTrace trace={trace} />}

    <section className="dash-card"><h3>What-if prediction</h3><div className="form-row">
      <label className="form-field"><span className="field-label">Student ID</span><Input type="number" value={whatIf.studentId} onChange={v => setWhatIf(x => ({ ...x, studentId: v }))} /></label>
      <label className="form-field"><span className="field-label">Term ID</span><Input type="number" value={whatIf.termId} onChange={v => setWhatIf(x => ({ ...x, termId: v }))} /></label>
    </div>
    {['attendance_pct_midterm', 'internal1_avg_pct', 'internal2_avg_pct'].map(key => <label className="form-field" key={key}><span className="field-label">{key.replaceAll('_', ' ')}</span><input type="range" min="0" max="100" value={whatIf[key]} onChange={e => setWhatIf(x => ({ ...x, [key]: e.target.value }))} /></label>)}
    <button className="btn btn-primary" onClick={predict} disabled={!whatIf.studentId || !whatIf.termId}>Predict</button>
    {prediction && <p><RiskBadge value={prediction.risk_level} /> Probability: {(Number(prediction.probability) * 100).toFixed(1)}% · model {prediction.model_version}</p>}
    </section>
    {recommendations.length > 0 && <section className="dash-card"><h3>Recommended subjects</h3><div className="table-wrapper"><table className="entity-table"><tbody>{recommendations.map(r => <tr key={r.subject_id}><td>{r.code}</td><td>{r.title}</td><td>{r.reason}</td></tr>)}</tbody></table></div></section>}
    {(model || metrics) && <section className="dash-card"><h3>Model Insights</h3><p>Version: {model?.model_version || '—'} (synthetic data; not production performance evidence)</p><MetricBars metrics={metrics?.metrics || metrics} /></section>}
    {user?.role === 'Admin' && <button className="btn btn-ghost" onClick={async () => { try { await riskApi.train(); await loadInsights(); } catch (e) { setError(e?.response?.data?.message || e.message); } }}>Retrain model</button>}
  </PageShell>;
}
