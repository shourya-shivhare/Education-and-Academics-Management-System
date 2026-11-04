import { useState, useEffect, useCallback } from 'react';
import { enrollmentApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const STATUS_OPTIONS = [
  { value: 'Enrolled',   label: 'Enrolled' },
  { value: 'Dropped',    label: 'Dropped' },
  { value: 'Completed',  label: 'Completed' },
  { value: 'Withdrawn',  label: 'Withdrawn' },
];

const COLUMNS = [
  { key: 'enrollmentId',  label: 'ID' },
  { key: 'studentName',   label: 'Student', render: r => r.studentName || `#${r.studentId}` },
  { key: 'rollNo',        label: 'Roll No' },
  { key: 'subjectCode',   label: 'Subject' },
  { key: 'termName',      label: 'Term' },
  { key: 'enrollDate',    label: 'Enrolled On' },
  { key: 'status',        label: 'Status', render: r => (
    <span className={`badge badge-${r.status?.toLowerCase()}`}>{r.status}</span>
  )},
  { key: 'grade',         label: 'Grade' },
];

const EMPTY_FORM = { studentId: '', offeringId: '' };

export default function EnrollmentPage() {
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 20;

  const [filterStudent, setFilterStudent]   = useState('');
  const [filterOffering, setFilterOffering] = useState('');
  const [filterStatus, setFilterStatus]     = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm]         = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving]     = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async (p = page, preserveTrace = false) => {
    setLoading(true); setError(null); if (!preserveTrace) setLastTrace(null);
    try {
      const data = await enrollmentApi.search({
        studentId:  filterStudent || undefined,
        offeringId: filterOffering || undefined,
        status:     filterStatus || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, filterStudent, filterOffering, filterStatus]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.studentId)  e.studentId  = 'Required';
    if (!form.offeringId) e.offeringId = 'Required';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setLastTrace(null);
    try {
      const payload = {
        studentId:  Number(form.studentId),
        offeringId: Number(form.offeringId),
      };
      await enrollmentApi.create(payload);
      setLastTrace(getLatestTrace()); setFormOpen(false); load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(getLatestTrace());
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    setDeleting(true); setLastTrace(null);
    try {
      // Backend uses sp_drop_enrollment via DELETE /enrollments with {studentId, offeringId}
      await enrollmentApi.drop({
        studentId: deleteTarget.studentId,
        offeringId: deleteTarget.offeringId,
      });
      setLastTrace(getLatestTrace()); setDeleteTarget(null); load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(getLatestTrace()); setDeleteTarget(null);
    } finally { setDeleting(false); }
  };

  const openCreate = () => { setForm(EMPTY_FORM); setFormErrors({}); setFormOpen(true); setTriggerError(null); };
  return (
    <PageShell title="Enrollment" subtitle="Manage student course enrollments"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Enroll Student</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <SearchBar value={filterStudent} onChange={setFilterStudent}
        onSearch={() => { setPage(0); load(0); }} placeholder="Student ID…">
        <Input value={filterOffering} onChange={setFilterOffering} placeholder="Offering ID" type="number" />
        <Select value={filterStatus} onChange={setFilterStatus} options={STATUS_OPTIONS} placeholder="All statuses" />
      </SearchBar>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onDelete={r => setDeleteTarget(r)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen} title="Enroll Student"
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}
        submitLabel="Enroll">
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />
        <div className="form-row">
          <FormField label="Student ID" required error={formErrors.studentId}>
            <Input type="number" value={form.studentId} onChange={v => setForm(f => ({ ...f, studentId: v }))} />
          </FormField>
          <FormField label="Offering ID" required error={formErrors.offeringId}>
            <Input type="number" value={form.offeringId} onChange={v => setForm(f => ({ ...f, offeringId: v }))} />
          </FormField>
        </div>
        <p className="form-hint">
          The <code>sp_enroll_student</code> stored procedure validates capacity and prerequisite rules.
        </p>
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Drop Enrollment"
        message={`Drop enrollment #${deleteTarget?.enrollmentId} for student ${deleteTarget?.studentName || deleteTarget?.studentId}? Attendance and marks for this enrollment will also be removed.`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
