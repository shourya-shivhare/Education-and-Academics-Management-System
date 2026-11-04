import { useState, useEffect, useCallback } from 'react';
import { facultyApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const DESIGNATION_OPTIONS = [
  { value: 'Professor',         label: 'Professor' },
  { value: 'Associate Professor', label: 'Associate Professor' },
  { value: 'Assistant Professor', label: 'Assistant Professor' },
  { value: 'Lecturer',          label: 'Lecturer' },
  { value: 'Adjunct',           label: 'Adjunct' },
];

const COLUMNS = [
  { key: 'facultyId',     label: 'ID' },
  { key: 'fullName',      label: 'Name', render: r => `${r.firstName} ${r.lastName}` },
  { key: 'email',         label: 'Email' },
  { key: 'designation',   label: 'Designation' },
  { key: 'departmentName',label: 'Department' },
  { key: 'specialization',label: 'Specialization' },
  { key: 'isActive',      label: 'Active', render: r => (
    <span className={`badge badge-${r.isActive ? 'active' : 'dropped'}`}>
      {r.isActive ? 'Yes' : 'No'}
    </span>
  )},
];

const EMPTY_FORM = {
  firstName: '', lastName: '', email: '', phone: '',
  dob: '', gender: 'Male', nationality: 'Indian',
  departmentId: '', designation: 'Assistant Professor',
  specialization: '', joiningDate: '', qualification: '',
  isActive: true,
};

export default function FacultyPage() {
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 20;

  const [searchName, setSearchName] = useState('');
  const [filterDept, setFilterDept] = useState('');

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
      const data = await facultyApi.search({
        name: searchName || undefined,
        departmentId: filterDept || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, searchName, filterDept]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.firstName.trim()) e.firstName = 'Required';
    if (!form.lastName.trim())  e.lastName  = 'Required';
    if (!form.email.trim())     e.email     = 'Required';
    if (!form.departmentId)     e.departmentId = 'Required';
    if (!form.joiningDate)      e.joiningDate  = 'Required';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setLastTrace(null);
    try {
      if (editRow) {
        await facultyApi.update(editRow.facultyId, {
          designation: form.designation,
          specialization: form.specialization,
          qualification: form.qualification,
          isActive: form.isActive,
        });
      } else {
        await facultyApi.create({
          firstName: form.firstName, lastName: form.lastName,
          email: form.email, phone: form.phone,
          dob: form.dob, gender: form.gender, nationality: form.nationality,
          departmentId: Number(form.departmentId),
          designation: form.designation,
          specialization: form.specialization,
          joiningDate: form.joiningDate,
          qualification: form.qualification,
        });
      }
      setLastTrace(getLatestTrace());
      setFormOpen(false);
      load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg);
      else setError(msg);
      setLastTrace(getLatestTrace());
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    setDeleting(true); setLastTrace(null);
    try {
      await facultyApi.delete(deleteTarget.facultyId);
      setLastTrace(getLatestTrace()); setDeleteTarget(null); load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg);
      else setError(msg);
      setLastTrace(getLatestTrace()); setDeleteTarget(null);
    } finally { setDeleting(false); }
  };

  const openCreate = () => { setForm(EMPTY_FORM); setFormErrors({}); setEditRow(null); setFormOpen(true); setTriggerError(null); };
  const openEdit   = (row) => {
    setForm({ ...EMPTY_FORM, designation: row.designation || 'Assistant Professor',
      specialization: row.specialization || '', qualification: row.qualification || '',
      isActive: row.isActive ?? true });
    setFormErrors({}); setEditRow(row); setFormOpen(true); setTriggerError(null);
  };

  return (
    <PageShell title="Faculty" subtitle="Manage faculty records"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Add Faculty</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <SearchBar value={searchName} onChange={setSearchName} onSearch={() => { setPage(0); load(0); }} placeholder="Search by name…">
        <Input value={filterDept} onChange={setFilterDept} placeholder="Dept ID" type="number" />
      </SearchBar>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit} onDelete={r => setDeleteTarget(r)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen} title={editRow ? `Edit Faculty #${editRow.facultyId}` : 'Add Faculty'}
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}>
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />
        {!editRow && (
          <>
            <div className="form-row">
              <FormField label="First Name" required error={formErrors.firstName}>
                <Input value={form.firstName} onChange={v => setForm(f => ({ ...f, firstName: v }))} />
              </FormField>
              <FormField label="Last Name" required error={formErrors.lastName}>
                <Input value={form.lastName} onChange={v => setForm(f => ({ ...f, lastName: v }))} />
              </FormField>
            </div>
            <FormField label="Email" required error={formErrors.email}>
              <Input type="email" value={form.email} onChange={v => setForm(f => ({ ...f, email: v }))} />
            </FormField>
            <div className="form-row">
              <FormField label="Department ID" required error={formErrors.departmentId}>
                <Input type="number" value={form.departmentId} onChange={v => setForm(f => ({ ...f, departmentId: v }))} />
              </FormField>
              <FormField label="Joining Date" required error={formErrors.joiningDate}>
                <Input type="date" value={form.joiningDate} onChange={v => setForm(f => ({ ...f, joiningDate: v }))} />
              </FormField>
            </div>
          </>
        )}
        <FormField label="Designation">
          <Select value={form.designation} onChange={v => setForm(f => ({ ...f, designation: v }))}
            options={DESIGNATION_OPTIONS} placeholder="" />
        </FormField>
        <FormField label="Specialization">
          <Input value={form.specialization} onChange={v => setForm(f => ({ ...f, specialization: v }))} placeholder="e.g. Machine Learning" />
        </FormField>
        <FormField label="Qualification">
          <Input value={form.qualification} onChange={v => setForm(f => ({ ...f, qualification: v }))} placeholder="Ph.D Computer Science" />
        </FormField>
        {editRow && (
          <FormField label="Active">
            <Select value={form.isActive ? 'true' : 'false'}
              onChange={v => setForm(f => ({ ...f, isActive: v === 'true' }))}
              options={[{ value: 'true', label: 'Yes' }, { value: 'false', label: 'No' }]} placeholder="" />
          </FormField>
        )}
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Delete Faculty"
        message={`Delete ${deleteTarget?.firstName} ${deleteTarget?.lastName}? This removes their teaching assignments too.`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
