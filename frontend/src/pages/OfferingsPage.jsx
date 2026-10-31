import { useState, useEffect, useCallback } from 'react';
import { offeringsApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import { useTrace } from '../store/TraceContext';

const STATUS_OPTIONS = [
  { value: 'Planned',   label: 'Planned' },
  { value: 'Active',    label: 'Active' },
  { value: 'Completed', label: 'Completed' },
  { value: 'Cancelled', label: 'Cancelled' },
];

const COLUMNS = [
  { key: 'offeringId',   label: 'ID' },
  { key: 'subjectCode',  label: 'Subject' },
  { key: 'subjectTitle', label: 'Title' },
  { key: 'facultyName',  label: 'Faculty', render: r => r.facultyName || '—' },
  { key: 'termName',     label: 'Term' },
  { key: 'sectionCode',  label: 'Section' },
  { key: 'capacity',     label: 'Cap' },
  { key: 'enrolled',     label: 'Enrolled' },
  { key: 'status',       label: 'Status', render: r => (
    <span className={`badge badge-${r.status?.toLowerCase()}`}>{r.status}</span>
  )},
];

const EMPTY_FORM = {
  subjectId: '', facultyId: '', termId: '', classroomId: '',
  sectionCode: '', capacity: 60, status: 'Planned',
};

export default function OfferingsPage() {
  const { trace: globalTrace } = useTrace();
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 20;

  const [filterTerm, setFilterTerm]     = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterSubject, setFilterSubject] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editRow, setEditRow]   = useState(null);
  const [form, setForm]         = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving]     = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async (p = page) => {
    setLoading(true); setError(null); setLastTrace(null);
    try {
      const data = await offeringsApi.search({
        termId: filterTerm || undefined,
        status: filterStatus || undefined,
        subjectId: filterSubject || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      setLastTrace(globalTrace);
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, filterTerm, filterStatus, filterSubject, globalTrace]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.subjectId)   e.subjectId   = 'Required';
    if (!form.facultyId)   e.facultyId   = 'Required';
    if (!form.termId)      e.termId      = 'Required';
    if (!form.sectionCode.trim()) e.sectionCode = 'Required';
    if (!form.capacity || form.capacity < 1) e.capacity = 'Min 1';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setLastTrace(null);
    try {
      const payload = {
        subjectId: Number(form.subjectId),
        facultyId: Number(form.facultyId),
        termId: Number(form.termId),
        classroomId: form.classroomId ? Number(form.classroomId) : null,
        sectionCode: form.sectionCode,
        capacity: Number(form.capacity),
        status: form.status,
      };
      if (editRow) await offeringsApi.update(editRow.offeringId, payload);
      else         await offeringsApi.create(payload);
      setLastTrace(globalTrace); setFormOpen(false); load(page);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(globalTrace);
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    setDeleting(true); setLastTrace(null);
    try {
      await offeringsApi.delete(deleteTarget.offeringId);
      setLastTrace(globalTrace); setDeleteTarget(null); load(page);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(globalTrace); setDeleteTarget(null);
    } finally { setDeleting(false); }
  };

  const openCreate = () => { setForm(EMPTY_FORM); setFormErrors({}); setEditRow(null); setFormOpen(true); setTriggerError(null); };
  const openEdit   = (row) => {
    setForm({ subjectId: row.subjectId || '', facultyId: row.facultyId || '',
      termId: row.termId || '', classroomId: row.classroomId || '',
      sectionCode: row.sectionCode || '', capacity: row.capacity || 60, status: row.status || 'Active' });
    setFormErrors({}); setEditRow(row); setFormOpen(true); setTriggerError(null);
  };

  return (
    <PageShell title="Course Offerings" subtitle="Schedule sections, assign faculty, manage capacity"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Add Offering</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <SearchBar value={filterSubject} onChange={setFilterSubject}
        onSearch={() => { setPage(0); load(0); }} placeholder="Filter by Subject ID…" type="number">
        <Input value={filterTerm} onChange={setFilterTerm} placeholder="Term ID" type="number" />
        <Select value={filterStatus} onChange={setFilterStatus} options={STATUS_OPTIONS} placeholder="All statuses" />
      </SearchBar>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit} onDelete={r => setDeleteTarget(r)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen} title={editRow ? `Edit Offering #${editRow.offeringId}` : 'Add Course Offering'}
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}>
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />
        <div className="form-row">
          <FormField label="Subject ID" required error={formErrors.subjectId}>
            <Input type="number" value={form.subjectId} onChange={v => setForm(f => ({ ...f, subjectId: v }))} />
          </FormField>
          <FormField label="Faculty ID" required error={formErrors.facultyId}>
            <Input type="number" value={form.facultyId} onChange={v => setForm(f => ({ ...f, facultyId: v }))} />
          </FormField>
        </div>
        <div className="form-row">
          <FormField label="Term ID" required error={formErrors.termId}>
            <Input type="number" value={form.termId} onChange={v => setForm(f => ({ ...f, termId: v }))} />
          </FormField>
          <FormField label="Classroom ID">
            <Input type="number" value={form.classroomId} onChange={v => setForm(f => ({ ...f, classroomId: v }))} placeholder="optional" />
          </FormField>
        </div>
        <div className="form-row">
          <FormField label="Section Code" required error={formErrors.sectionCode}>
            <Input value={form.sectionCode} onChange={v => setForm(f => ({ ...f, sectionCode: v }))} placeholder="A" />
          </FormField>
          <FormField label="Capacity" required error={formErrors.capacity}>
            <Input type="number" min="1" value={form.capacity} onChange={v => setForm(f => ({ ...f, capacity: v }))} />
          </FormField>
          <FormField label="Status">
            <Select value={form.status} onChange={v => setForm(f => ({ ...f, status: v }))} options={STATUS_OPTIONS} placeholder="" />
          </FormField>
        </div>
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Delete Offering"
        message={`Delete offering #${deleteTarget?.offeringId} (${deleteTarget?.subjectCode} – Section ${deleteTarget?.sectionCode})? All enrollments will cascade-delete.`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
