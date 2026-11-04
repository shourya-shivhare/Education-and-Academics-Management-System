import { useState, useEffect, useCallback } from 'react';
import { marksApi } from '../api/entities';
import {
  PageShell, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert, ConflictAlert,
  InlineTrace, FormField, Input,
} from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const COLUMNS = [
  { key: 'enrollmentId',  label: 'Enrollment' },
  { key: 'studentId',     label: 'Student' },
  { key: 'rollNo',        label: 'Roll No' },
  { key: 'assessmentNo',  label: 'Assessment #' },
  { key: 'assessmentTitle', label: 'Assessment' },
  { key: 'marksObtained', label: 'Obtained' },
  { key: 'maxMarks',      label: 'Max' },
  { key: 'percentage',    label: '%', render: r =>
    r.maxMarks ? `${((r.marksObtained / r.maxMarks) * 100).toFixed(1)}%` : '—'
  },
  { key: 'version',       label: 'ver', render: r => (
    <code className="version-badge">v{r.version ?? 0}</code>
  )},
];

const EMPTY_FORM = {
  enrollmentId: '', offeringId: '', assessmentNo: '',
  marksObtained: '', version: null,
};

export default function MarksPage() {
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
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async (p = page, preserveTrace = false) => {
    setLoading(true); setError(null); if (!preserveTrace) setLastTrace(null);
    try {
      const data = await marksApi.search({
        offeringId: filterOffering || undefined,
        studentId:  filterStudent  || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, filterOffering, filterStudent]);

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
        version:       form.version !== null && form.version !== '' ? Number(form.version) : null,
      });
      setLastTrace(getLatestTrace()); setFormOpen(false); load(page, true);
    } catch (e) {
      const status = e?.response?.status;
      const msg    = e?.response?.data?.message || e.message;
      if (status === 409 && form.version !== null) {
        // Optimistic lock conflict
        setConflictError(msg);
        setLastTrace(getLatestTrace());
      } else if (status === 409) {
        setTriggerError(msg);
        setLastTrace(getLatestTrace());
      } else {
        setError(msg);
        setLastTrace(getLatestTrace());
      }
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    setDeleting(true); setLastTrace(null);
    try {
      await marksApi.delete({
        enrollmentId: deleteTarget.enrollmentId,
        offeringId: deleteTarget.offeringId,
        assessmentNo: deleteTarget.assessmentNo,
      });
      setLastTrace(getLatestTrace());
      setDeleteTarget(null);
      load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(getLatestTrace());
      setDeleteTarget(null);
    } finally { setDeleting(false); }
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
      version: row.version ?? 0,
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

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit}
        onDelete={row => setDeleteTarget(row)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />
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

        <FormField label="Marks Obtained" required error={formErrors.marksObtained}>
            <Input type="number" min="0" step="0.5" value={form.marksObtained}
              onChange={v => setForm(f => ({ ...f, marksObtained: v }))} placeholder="e.g. 42.5" />
        </FormField>

        {editRow && (
          <p className="form-hint">
            💡 If you get a <strong>409 Conflict</strong>, click <strong>↻ Reload</strong> to fetch the latest version, then re-enter your changes.
          </p>
        )}
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Delete Marks Record"
        message={`Delete marks for enrollment #${deleteTarget?.enrollmentId}, assessment #${deleteTarget?.assessmentNo}?`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
