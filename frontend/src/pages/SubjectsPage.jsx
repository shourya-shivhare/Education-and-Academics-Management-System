import { useState, useEffect, useCallback } from 'react';
import { subjectsApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const COLUMNS = [
  { key: 'subjectId',      label: 'ID' },
  { key: 'code',           label: 'Code' },
  { key: 'title',          label: 'Title' },
  { key: 'credits',        label: 'Credits' },
  { key: 'semester',       label: 'Sem' },
  { key: 'departmentName', label: 'Department' },
  { key: 'description',    label: 'Description', render: r => (
    <span title={r.description}>{r.description?.substring(0, 50)}{r.description?.length > 50 ? '…' : ''}</span>
  )},
];

const EMPTY_FORM = {
  code: '', title: '', credits: 3, semester: 1,
  departmentId: '', programId: '', description: '',
};

export default function SubjectsPage() {
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 20;

  const [searchTitle, setSearchTitle] = useState('');
  const [filterDept, setFilterDept]   = useState('');

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
      const data = await subjectsApi.search({
        title: searchTitle || undefined,
        departmentId: filterDept || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, searchTitle, filterDept]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.code.trim())  e.code  = 'Required';
    if (!form.title.trim()) e.title = 'Required';
    if (!form.departmentId) e.departmentId = 'Required';
    if (!form.credits || form.credits < 1) e.credits = 'Min 1';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setLastTrace(null);
    try {
      const payload = {
        code: form.code, title: form.title,
        credits: Number(form.credits), semester: Number(form.semester),
        departmentId: Number(form.departmentId),
        programId: form.programId ? Number(form.programId) : null,
        description: form.description,
      };
      if (editRow) await subjectsApi.update(editRow.subjectId, payload);
      else         await subjectsApi.create(payload);
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
      await subjectsApi.delete(deleteTarget.subjectId);
      setLastTrace(getLatestTrace()); setDeleteTarget(null); load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(getLatestTrace()); setDeleteTarget(null);
    } finally { setDeleting(false); }
  };

  const openCreate = () => { setForm(EMPTY_FORM); setFormErrors({}); setEditRow(null); setFormOpen(true); setTriggerError(null); };
  const openEdit   = (row) => {
    setForm({ code: row.code, title: row.title, credits: row.credits, semester: row.semester,
      departmentId: row.departmentId || '', programId: row.programId || '', description: row.description || '' });
    setFormErrors({}); setEditRow(row); setFormOpen(true); setTriggerError(null);
  };

  return (
    <PageShell title="Subjects" subtitle="Course catalog — all departments"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Add Subject</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <SearchBar value={searchTitle} onChange={setSearchTitle}
        onSearch={() => { setPage(0); load(0); }} placeholder="Search by title…">
        <Input value={filterDept} onChange={setFilterDept} placeholder="Dept ID" type="number" />
      </SearchBar>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit} onDelete={r => setDeleteTarget(r)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen} title={editRow ? `Edit Subject #${editRow.subjectId}` : 'Add Subject'}
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}>
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />
        <div className="form-row">
          <FormField label="Code" required error={formErrors.code}>
            <Input value={form.code} onChange={v => setForm(f => ({ ...f, code: v }))} placeholder="CS301" />
          </FormField>
          <FormField label="Credits" required error={formErrors.credits}>
            <Input type="number" min="1" max="6" value={form.credits} onChange={v => setForm(f => ({ ...f, credits: v }))} />
          </FormField>
        </div>
        <FormField label="Title" required error={formErrors.title}>
          <Input value={form.title} onChange={v => setForm(f => ({ ...f, title: v }))} placeholder="Database Management Systems" />
        </FormField>
        <div className="form-row">
          <FormField label="Department ID" required error={formErrors.departmentId}>
            <Input type="number" value={form.departmentId} onChange={v => setForm(f => ({ ...f, departmentId: v }))} />
          </FormField>
          <FormField label="Program ID">
            <Input type="number" value={form.programId} onChange={v => setForm(f => ({ ...f, programId: v }))} placeholder="optional" />
          </FormField>
          <FormField label="Semester">
            <Input type="number" min="1" max="12" value={form.semester} onChange={v => setForm(f => ({ ...f, semester: v }))} />
          </FormField>
        </div>
        <FormField label="Description">
          <textarea className="form-control" rows={3} value={form.description}
            onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
            placeholder="Brief course description…" />
        </FormField>
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Delete Subject"
        message={`Delete subject "${deleteTarget?.code} – ${deleteTarget?.title}"? All offerings of this subject will also be removed.`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
