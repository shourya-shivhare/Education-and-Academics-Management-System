import { useState, useEffect, useCallback } from 'react';
import { studentsApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import TracePanel from '../components/TracePanel';
import { getLatestTrace } from '../store/traceStore';

const STATUS_OPTIONS = [
  { value: 'Active',    label: 'Active' },
  { value: 'Graduated', label: 'Graduated' },
  { value: 'Dropped',   label: 'Dropped' },
  { value: 'Suspended', label: 'Suspended' },
];

const COLUMNS = [
  { key: 'studentId',   label: 'ID' },
  { key: 'rollNo',      label: 'Roll No' },
  { key: 'fullName',    label: 'Name', render: r => `${r.firstName} ${r.lastName}` },
  { key: 'email',       label: 'Email' },
  { key: 'programName', label: 'Program' },
  { key: 'status',      label: 'Status', render: r => (
    <span className={`badge badge-${r.status?.toLowerCase()}`}>{r.status}</span>
  )},
  { key: 'currentSemester', label: 'Sem' },
];

const EMPTY_FORM = {
  firstName: '', lastName: '', email: '', phone: '',
  dob: '', gender: 'Male', nationality: 'Indian',
  addressLine: '', city: '', state: '', pinCode: '',
  programId: '', admissionDate: '', rollNo: '',
  status: 'Active', currentSemester: 1,
};

export default function StudentsPage() {
  const [rows, setRows]             = useState([]);
  const [loading, setLoading]       = useState(false);
  const [lastTrace, setLastTrace]   = useState(null);
  const [error, setError]           = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]             = useState(0);
  const [total, setTotal]           = useState(0);
  const PAGE_SIZE = 20;

  // Search filters
  const [searchName, setSearchName] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  // Modal state
  const [formOpen, setFormOpen]     = useState(false);
  const [editRow, setEditRow]       = useState(null);
  const [form, setForm]             = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving]         = useState(false);

  // Delete state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting]     = useState(false);

  const load = useCallback(async (p = page, preserveTrace = false) => {
    setLoading(true);
    setError(null);
    if (!preserveTrace) setLastTrace(null);
    try {
      const data = await studentsApi.search({
        name: searchName || undefined,
        status: filterStatus || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) {
      setError(e?.response?.data?.message || e.message);
    } finally {
      setLoading(false);
    }
  }, [page, searchName, filterStatus]);

  useEffect(() => { load(0); }, []); // initial load

  const handleSearch = () => { setPage(0); load(0); };

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setFormErrors({});
    setEditRow(null);
    setFormOpen(true);
    setTriggerError(null);
  };

  const openEdit = (row) => {
    setForm({
      firstName: row.firstName || '',
      lastName: row.lastName || '',
      email: row.email || '',
      phone: '',
      dob: row.dob || '',
      gender: row.gender || 'Male',
      nationality: row.nationality || 'Indian',
      addressLine: row.addressLine || '',
      city: row.city || '',
      state: row.state || '',
      pinCode: row.pinCode || '',
      programId: row.programId || '',
      admissionDate: row.admissionDate || '',
      rollNo: row.rollNo || '',
      status: row.status || 'Active',
      currentSemester: row.currentSemester || 1,
    });
    setFormErrors({});
    setEditRow(row);
    setFormOpen(true);
    setTriggerError(null);
  };

  const validate = () => {
    const e = {};
    if (!form.firstName.trim()) e.firstName = 'Required';
    if (!form.lastName.trim())  e.lastName  = 'Required';
    if (!form.email.trim())     e.email     = 'Required';
    if (!form.rollNo.trim())    e.rollNo    = 'Required';
    if (!form.programId)        e.programId = 'Required';
    if (!form.admissionDate)    e.admissionDate = 'Required';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true);
    setTriggerError(null);
    setLastTrace(null);
    try {
      if (editRow) {
        await studentsApi.update(editRow.studentId, {
          addressLine: form.addressLine,
          city: form.city,
          state: form.state,
          pinCode: form.pinCode,
          status: form.status,
          currentSemester: Number(form.currentSemester),
        });
      } else {
        await studentsApi.create({
          firstName: form.firstName,
          lastName: form.lastName,
          email: form.email,
          phone: form.phone,
          dob: form.dob,
          gender: form.gender,
          nationality: form.nationality,
          addressLine: form.addressLine,
          city: form.city,
          state: form.state,
          pinCode: form.pinCode,
          programId: Number(form.programId),
          admissionDate: form.admissionDate,
          rollNo: form.rollNo,
          status: form.status,
          currentSemester: Number(form.currentSemester),
        });
      }
      setLastTrace(getLatestTrace());
      setFormOpen(false);
      load(page, true);
    } catch (e) {
      const status = e?.response?.status;
      const msg    = e?.response?.data?.message || e.message;
      if (status === 409) setTriggerError(msg);
      else setError(msg);
      setLastTrace(getLatestTrace());
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    setLastTrace(null);
    try {
      await studentsApi.delete(deleteTarget.studentId);
      setLastTrace(getLatestTrace());
      setDeleteTarget(null);
      load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg);
      else setError(msg);
      setLastTrace(getLatestTrace());
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <PageShell
      title="Students"
      subtitle="Manage student records — ADMIN and REGISTRAR only"
      actions={
        <button className="btn btn-primary" onClick={openCreate}>+ Add Student</button>
      }
    >
      {/* Alerts */}
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      {/* Search */}
      <SearchBar value={searchName} onChange={setSearchName} onSearch={handleSearch} placeholder="Search by name…">
        <Select
          value={filterStatus}
          onChange={v => { setFilterStatus(v); }}
          options={STATUS_OPTIONS}
          placeholder="All statuses"
        />
      </SearchBar>

      {/* Table */}
      <DataTable
        columns={COLUMNS}
        rows={rows}
        loading={loading}
        onEdit={openEdit}
        onDelete={row => setDeleteTarget(row)}
        emptyMsg="No students found. Try a different search."
      />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />

      {/* Inline Trace after search */}
      {lastTrace && <InlineTrace trace={lastTrace} />}

      {/* Form Modal */}
      <FormModal
        open={formOpen}
        title={editRow ? `Edit Student #${editRow.studentId}` : 'Add New Student'}
        onClose={() => setFormOpen(false)}
        onSubmit={handleSave}
        loading={saving}
      >
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

        {!editRow && (
          <>
            <div className="form-row">
              <FormField label="First Name" required error={formErrors.firstName}>
                <Input value={form.firstName} onChange={v => setForm(f => ({ ...f, firstName: v }))} placeholder="Alice" />
              </FormField>
              <FormField label="Last Name" required error={formErrors.lastName}>
                <Input value={form.lastName} onChange={v => setForm(f => ({ ...f, lastName: v }))} placeholder="Smith" />
              </FormField>
            </div>
            <FormField label="Email" required error={formErrors.email}>
              <Input type="email" value={form.email} onChange={v => setForm(f => ({ ...f, email: v }))} placeholder="alice@college.edu" />
            </FormField>
            <div className="form-row">
              <FormField label="Roll No" required error={formErrors.rollNo}>
                <Input value={form.rollNo} onChange={v => setForm(f => ({ ...f, rollNo: v }))} placeholder="CS2024001" />
              </FormField>
              <FormField label="Program ID" required error={formErrors.programId}>
                <Input type="number" value={form.programId} onChange={v => setForm(f => ({ ...f, programId: v }))} placeholder="1" />
              </FormField>
            </div>
            <div className="form-row">
              <FormField label="Date of Birth">
                <Input type="date" value={form.dob} onChange={v => setForm(f => ({ ...f, dob: v }))} />
              </FormField>
              <FormField label="Admission Date" required error={formErrors.admissionDate}>
                <Input type="date" value={form.admissionDate} onChange={v => setForm(f => ({ ...f, admissionDate: v }))} />
              </FormField>
            </div>
            <FormField label="Phone">
              <Input value={form.phone} onChange={v => setForm(f => ({ ...f, phone: v }))} placeholder="+91-9999999999" />
            </FormField>
          </>
        )}

        {/* Mutable fields (shown for both create and edit) */}
        <div className="form-row">
          <FormField label="Status">
            <Select value={form.status} onChange={v => setForm(f => ({ ...f, status: v }))} options={STATUS_OPTIONS} placeholder="" />
          </FormField>
          <FormField label="Current Semester">
            <Input type="number" min="1" max="12" value={form.currentSemester} onChange={v => setForm(f => ({ ...f, currentSemester: v }))} />
          </FormField>
        </div>
        <FormField label="Address Line">
          <Input value={form.addressLine} onChange={v => setForm(f => ({ ...f, addressLine: v }))} placeholder="123 Main St" />
        </FormField>
        <div className="form-row">
          <FormField label="City">
            <Input value={form.city} onChange={v => setForm(f => ({ ...f, city: v }))} placeholder="Mumbai" />
          </FormField>
          <FormField label="State">
            <Input value={form.state} onChange={v => setForm(f => ({ ...f, state: v }))} placeholder="Maharashtra" />
          </FormField>
          <FormField label="PIN Code">
            <Input value={form.pinCode} onChange={v => setForm(f => ({ ...f, pinCode: v }))} placeholder="400001" />
          </FormField>
        </div>
      </FormModal>

      {/* Delete Confirm */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Student"
        message={`Permanently delete ${deleteTarget?.firstName} ${deleteTarget?.lastName} (Roll: ${deleteTarget?.rollNo})? This will cascade to enrollments and attendance.`}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
        loading={deleting}
      />
    </PageShell>
  );
}
