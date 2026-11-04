import { useState } from 'react';
import { labsApi } from '../api/entities';
import { PageShell, ErrorAlert, InlineTrace } from '../components/EntityComponents';

const ACID = [
  ['Atomicity', 'atomicity', () => labsApi.atomicity(false)],
  ['Consistency', 'consistency', () => labsApi.consistency()],
  ['Isolation', 'isolation', () => labsApi.isolation()],
  ['Durability', 'durability', () => labsApi.durability()],
  ['Verify durability', 'durabilityVerify', () => labsApi.durabilityVerify()],
];
const CONCURRENCY = [
  ['Last-seat race', 'lastSeatRace', () => labsApi.lastSeatRace(false)],
  ['Lost update', 'lostUpdate', () => labsApi.lostUpdate(false)],
  ['Deadlock', 'deadlock', () => labsApi.deadlock()],
];

export default function LabPage({ kind }) {
  const scenarios = kind === 'acid' ? ACID : CONCURRENCY;
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const run = async fn => { setError(null); try { setResult(await fn()); } catch (e) { setError(e?.response?.data?.message || e.message); } };
  return <PageShell title={kind === 'acid' ? 'ACID Lab' : 'Concurrency Lab'} subtitle="Run a database scenario and inspect each SQL step">
    <ErrorAlert message={error} onClose={() => setError(null)} />
    <div className="entity-actions">{scenarios.map(([label, key, fn]) => <button key={key} className="btn btn-primary" onClick={() => run(fn)}>{label}</button>)}</div>
    {result && <div className="dash-card"><h3>{result.scenario} · {result.outcome}</h3><ol>{(result.steps || []).map((step, i) => <li key={i}>{step}</li>)}</ol><InlineTrace trace={result.sqlTrace || []} /></div>}
  </PageShell>;
}
