import { useState } from 'react';
import { riskApi } from '../api/entities';
import { ErrorAlert, InlineTrace, PageShell, Input } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

export default function RiskPage() {
  const [studentId, setStudentId] = useState('');
  const [termId, setTermId] = useState('');
  const [result, setResult] = useState(null);
  const [trace, setTrace] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const predict = async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      setResult(await riskApi.predict({ studentId: Number(studentId), termId: Number(termId) }));
      setTrace(getLatestTrace());
    } catch (e) {
      setError(e?.response?.data?.message || e?.response?.data?.error?.message || e.message);
      setTrace(getLatestTrace());
    } finally {
      setLoading(false);
    }
  };

  return <PageShell title="AI Risk Dashboard" subtitle="Mid-term leakage-safe prediction from the internal ML service">
    <ErrorAlert message={error} onClose={() => setError(null)} />
    <section className="dash-card">
      <h3>Predict student risk</h3>
      <div className="form-grid">
        <label className="form-field"><span className="field-label">Student ID</span><Input type="number" min="1" value={studentId} onChange={setStudentId} /></label>
        <label className="form-field"><span className="field-label">Term ID</span><Input type="number" min="1" value={termId} onChange={setTermId} /></label>
      </div>
      <button className="btn btn-primary" onClick={predict} disabled={loading || !studentId || !termId}>
        {loading ? 'Predicting…' : 'Predict risk'}
      </button>
      <p className="form-hint">The model reads only <code>v_ml_features</code>; final grades and detention are label-only.</p>
    </section>
    {result && <section className={`dash-card ${result.atRisk ? 'risk-card-high' : 'risk-card-low'}`}>
      <h3>{result.atRisk ? 'At risk' : 'Lower predicted risk'}</h3>
      <p className="risk-score">{(Number(result.atRiskProbability) * 100).toFixed(1)}%</p>
      <p>Student {result.studentId}, term {result.termId} · model {result.modelVersion}</p>
      <div className="table-wrapper"><table className="entity-table"><thead><tr><th>Feature</th><th>Mid-term value</th></tr></thead><tbody>
        {Object.entries(result.features || {}).map(([key, value]) => <tr key={key}><td>{key}</td><td>{String(value ?? '—')}</td></tr>)}
      </tbody></table></div>
    </section>}
    {trace.length > 0 && <InlineTrace trace={trace} />}
  </PageShell>;
}
