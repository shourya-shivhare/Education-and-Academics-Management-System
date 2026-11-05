import { useEffect, useMemo, useState } from 'react';
import { reportsApi } from '../api/entities';
import { useAuth } from '../auth/AuthContext';
import { ErrorAlert, InlineTrace, Input, PageShell } from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const field = (row, ...names) => names.map(name => row[name]).find(value => value !== undefined && value !== null);

export default function TranscriptPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [term, setTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState([]);

  const load = async () => {
    if (!user?.personId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await reportsApi.transcript(user.personId);
      setRows(Array.isArray(data) ? data : []);
      setTrace(getLatestTrace());
    } catch (e) {
      setError(e?.response?.data?.message || e?.response?.data?.error?.message || e.message);
      setTrace(getLatestTrace());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [user?.personId]);

  const terms = useMemo(() => [...new Set(rows.map(row => field(row, 'term_name', 'TERM_NAME')).filter(Boolean))], [rows]);
  const visibleRows = term ? rows.filter(row => field(row, 'term_name', 'TERM_NAME') === term) : rows;
  const totals = useMemo(() => {
    const credits = visibleRows.reduce((sum, row) => sum + Number(field(row, 'credits', 'CREDITS') || 0), 0);
    const quality = visibleRows.reduce((sum, row) => sum + Number(field(row, 'quality_points', 'QUALITY_POINTS') || 0), 0);
    return { credits, gpa: credits ? quality / credits : null };
  }, [visibleRows]);

  const download = async () => {
    try {
      const response = await reportsApi.transcriptCsv(user.personId);
      const url = URL.createObjectURL(response);
      const link = document.createElement('a');
      link.href = url;
      link.download = `transcript_student_${user.personId}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e?.response?.data?.message || e.message);
    }
  };

  return (
    <PageShell title="My Transcript" subtitle="Academic history from the v_student_transcript database view"
      actions={<><button className="btn btn-outline" onClick={() => window.print()}>Print</button><button className="btn btn-primary" onClick={download}>Download CSV</button></>}>
      <ErrorAlert message={error} onClose={() => setError(null)} />
      <section className="dash-card transcript-summary">
        <div><strong>Student ID</strong><span>{user?.personId || '—'}</span></div>
        <div><strong>Subjects shown</strong><span>{visibleRows.length}</span></div>
        <div><strong>Credits</strong><span>{totals.credits}</span></div>
        <div><strong>Quality points</strong><span>{totals.gpa == null ? '—' : totals.gpa.toFixed(2)} GPA</span></div>
      </section>
      <section className="dash-card">
        <div className="entity-actions">
          <label className="form-field"><span className="field-label">Term filter</span>
            <select className="form-control" value={term} onChange={e => setTerm(e.target.value)}>
              <option value="">All terms</option>{terms.map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <button className="btn btn-outline" onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
        </div>
        {loading ? <div className="table-loading"><div className="spinner" /><span>Loading transcript…</span></div> :
          <div className="table-wrapper"><table className="entity-table"><thead><tr>
            <th>Term</th><th>Code</th><th>Subject</th><th>Credits</th><th>Marks</th><th>Grade</th><th>Grade point</th><th>Instructor</th>
          </tr></thead><tbody>
            {visibleRows.length === 0 ? <tr><td colSpan="8" className="empty-row">No transcript records found.</td></tr> :
              visibleRows.map((row, index) => <tr key={`${field(row, 'term_name', 'TERM_NAME')}-${field(row, 'subject_code', 'SUBJECT_CODE')}-${index}`}>
                <td>{field(row, 'term_name', 'TERM_NAME') || '—'}</td>
                <td>{field(row, 'subject_code', 'SUBJECT_CODE') || '—'}</td>
                <td>{field(row, 'subject_title', 'SUBJECT_TITLE') || '—'}</td>
                <td>{field(row, 'credits', 'CREDITS') ?? '—'}</td>
                <td>{field(row, 'final_marks', 'FINAL_MARKS') ?? '—'}</td>
                <td>{field(row, 'grade', 'GRADE') || '—'}</td>
                <td>{field(row, 'grade_point', 'GRADE_POINT') ?? '—'}</td>
                <td>{field(row, 'instructor_name', 'INSTRUCTOR_NAME') || '—'}</td>
              </tr>)}
          </tbody></table></div>}
      </section>
      {trace.length > 0 && <InlineTrace trace={trace} />}
    </PageShell>
  );
}
