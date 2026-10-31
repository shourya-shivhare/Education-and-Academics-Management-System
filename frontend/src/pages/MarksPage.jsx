import { useState, useEffect, useCallback } from 'react';
import { marksApi } from '../api/entities';
import {
  PageShell, DataTable, Pagination,
  FormModal, TriggerAlert, ErrorAlert, ConflictAlert,
  InlineTrace, FormField, Input,
} from '../components/EntityComponents';
import { useTrace } from '../store/TraceContext';

const COLUMNS = [
  { key: 'enrollmentId',  label: 'Enrollment' },
  { key: 'studentName',   label: 'Student', render: r => r.studentName || `#${r.studentId}` },
  { key: 'rollNo',        label: 'Roll No' },
  { key: 'assessmentNo',  label: 'Assessment #' },
  { key: 'assessmentName',label: 'Assessment' },
  { key: 'marksObtained', label: 'Obtained' },
  { key: 'maxMarks',      label: 'Max' },
  { key: 'percentage',    label: '%', render: r =>
    r.maxMarks ? `${((r.marksObtained / r.maxMarks) * 100).toFixed(1)}%` : '—'
  },
  { key: 'gradePoint',    label: 'Grade Pt' },
  { key: 'version',       label: 'ver', render: r => (
    <code className="version-badge">v{r.version ?? 0}</code>
  )},
];

const EMPTY_FORM = {
  enrollmentId: '', offeringId: '', assessmentNo: '',
  marksObtained: '', maxMarks: '', version: null,
};

export default function MarksPage() {
  const { trace: globalTrace } = useTrace();
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError]   = useState(null);
  const [conflictError, setConflictError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 25;

  const [filterOffering, setFilterOffering] = useState('');
  const [filterStudent, setFilterStudent]   = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editRow, setEditRow]   = useState(null);
  const [form, setForm]         = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving]     = useState(false);

  const load = useCallback(async (p = page) => {
    setLoading(true); setError(null); setLastTrace(null);
    try {
      const data = await marksApi.search({
        offeringId: filterOffering || undefined,
        studentId:  filterStudent  || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      setLastTrace(globalTrace);
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, filterOffering, filterStudent, globalTrace]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.enrollmentId) e.enrollmentId = 'Required';
    if (!form.offeringId)   e.offeringId   = 'Required';
    if (!form.assessmentNo) e.assessmentNo  = 'Required';
    if (form.marksObtained === '' || form.marksObtained === null) e.marksObtained = 'Required';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setConflictError(null); setLastTrace(null);
    try {
      // POST with version=null → INSERT; version set → optimistic UPDATE
      await marksApi.upsert({
        enrollmentId:  Number(form.enrollmentId),
        offeringId:    Number(form.offeringId),
        assessmentNo:  Number(form.assessmentNo),
        marksObtained: parseFloat(form.marksObtained),
        maxMarks:      form.maxMarks ? parseFloat(form.maxMarks) : null,
        version:       form.version !== null && form.version !== '' ? Number(form.version) : null,
      });
      setLastTrace(globalTrace); setFormOpen(false); load(page);
    } catch (e) {
      const status = e?.response?.status;
      const msg    = e?.response?.data?.message || e.message;
      if (status === 409 && form.version !== null) {
        // Optimistic lock conflict
        setConflictError(msg);
        setLastTrace(globalTrace);
      } else if (status === 409) {
        setTriggerError(msg);
        setLastTrace(globalTrace);
      } else {
        setError(msg);
        setLastTrace(globalTrace);
      }
    } finally { setSaving(false); }
  };

  /**
   * Re-fetch the latest version of this marks record so user can retry.
   */
  const handleConflictReload = async () => {
    if (!editRow) return;
    try {
      const fresh = await marksApi.getByPk(
        editRow.enrollmentId, editRow.offeringId, editRow.assessmentNo
      );
      if (fresh) {
        setForm(f => ({ ...f, version: fresh.version, marksObtained: fresh.marksObtained }));
        setEditRow(fresh);
      }
      setConflictError(null);
    } catch (e) {
      setError('Could not reload: ' + (e?.response?.data?.message || e.message));
    }
  };

  const openCreate = () => {
    setForm(EMPTY_FORM); setFormErrors({});
    setEditRow(null); setFormOpen(true);
    setTriggerError(null); setConflictError(null);
  };

  const openEdit = (row) => {
    setForm({
      enrollmentId: row.enrollmentId, offeringId: row.offeringId,
      assessmentNo: row.assessmentNo, marksObtained: row.marksObtained,
      maxMarks: row.maxMarks ?? '', version: row.version ?? 0,
    });
    setFormErrors({}); setEditRow(row); setFormOpen(true);
    setTriggerError(null); setConflictError(null);
  };

  return (
    <PageShell title="Marks Entry" subtitle="Enter and update assessment marks — FACULTY and ADMIN"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Enter Marks</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <div className="filter-row">
        <Input value={filterOffering} onChange={setFilterOffering} placeholder="Offering ID" type="number" />
        <Input value={filterStudent}  onChange={setFilterStudent}  placeholder="Student ID"  type="number" />
        <button className="btn btn-primary" onClick={() => { setPage(0); load(0); }}>🔍 Filter</button>
      </div>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen}
        title={editRow ? `Edit Marks (Enrollment #${editRow.enrollmentId}, Assessment #${editRow.assessmentNo})` : 'Enter Marks'}
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}
        submitLabel={editRow ? 'Update' : 'Save Marks'}>

        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

        {/* Optimistic-lock conflict banner with reload button */}
        <ConflictAlert
          message={conflictError}
          onReload={handleConflictReload}
          onClose={() => setConflictError(null)}
        />

        {editRow && (
          <div className="lock-info">
            <span className="lock-label">🔒 Optimistic Lock</span>
            <code>version = {form.version}</code>
            <span className="lock-hint">The server will reject this save if the record was modified by someone else since you loaded it.</span>
          </div>
        )}

        {!editRow && (
          <div className="form-row">
            <FormField label="Enrollment ID" required error={formErrors.enrollmentId}>
              <Input type="number" value={form.enrollmentId} onChange={v => setForm(f => ({ ...f, enrollmentId: v }))} />
            </FormField>
            <FormField label="Offering ID" required error={formErrors.offeringId}>
              <Input type="number" value={form.offeringId} onChange={v => setForm(f => ({ ...f, offeringId: v }))} />
            </FormField>
            <FormField label="Assessment #" required error={formErrors.assessmentNo}>
              <Input type="number" min="1" value={form.assessmentNo} onChange={v => setForm(f => ({ ...f, assessmentNo: v }))} />
            </FormField>
          </div>
        )}

        <div className="form-row">
          <FormField label="Marks Obtained" required error={formErrors.marksObtained}>
            <Input type="number" min="0" step="0.5" value={form.marksObtained}
              onChange={v => setForm(f => ({ ...f, marksObtained: v }))} placeholder="e.g. 42.5" />
          </FormField>
          <FormField label="Max Marks">
            <Input type="number" min="1" step="0.5" value={form.maxMarks}
              onChange={v => setForm(f => ({ ...f, maxMarks: v }))} placeholder="e.g. 50" />
          </FormField>
        </div>

        {editRow && (
          <p className="form-hint">
            💡 If you get a <strong>409 Conflict</strong>, click <strong>↻ Reload</strong> to fetch the latest version, then re-enter your changes.
          </p>
        )}
      </FormModal>
    </PageShell>
  );
}
