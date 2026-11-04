import { useState, useEffect, useCallback } from 'react';
import { attendanceApi } from '../api/entities';
import {
  PageShell, SearchBar, DataTable, Pagination,
  ConfirmDialog, FormModal, TriggerAlert, ErrorAlert,
  InlineTrace, FormField, Input, Select,
} from '../components/EntityComponents';
import { getLatestTrace } from '../store/traceStore';

const STATUS_OPTIONS = [
  { value: 'Present', label: 'Present' },
  { value: 'Absent',  label: 'Absent' },
  { value: 'Late',    label: 'Late' },
  { value: 'Excused', label: 'Excused' },
];

const COLUMNS = [
  { key: 'attendanceId',  label: 'ID' },
  { key: 'studentName',   label: 'Student', render: r => r.studentName || `#${r.studentId}` },
  { key: 'rollNo',        label: 'Roll No' },
  { key: 'subjectCode',   label: 'Subject' },
  { key: 'classDate',     label: 'Date' },
  { key: 'status',        label: 'Status', render: r => (
    <span className={`badge badge-attendance-${r.status?.toLowerCase()}`}>{r.status}</span>
  )},
  { key: 'remarks',       label: 'Remarks' },
];

const EMPTY_FORM = {
  enrollmentId: '', offeringId: '', classDate: '', status: 'Present', remarks: '',
};

export default function AttendancePage() {
  const [rows, setRows]         = useState([]);
  const [loading, setLoading]   = useState(false);
  const [lastTrace, setLastTrace] = useState(null);
  const [error, setError]       = useState(null);
  const [triggerError, setTriggerError] = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const PAGE_SIZE = 25;

  const [filterOffering, setFilterOffering] = useState('');
  const [filterStudent, setFilterStudent]   = useState('');
  const [filterFrom, setFilterFrom]         = useState('');
  const [filterTo, setFilterTo]             = useState('');

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
      const data = await attendanceApi.search({
        offeringId: filterOffering || undefined,
        studentId:  filterStudent  || undefined,
        fromDate:   filterFrom     || undefined,
        toDate:     filterTo       || undefined,
        page: p, size: PAGE_SIZE,
      });
      setRows(Array.isArray(data) ? data : []);
      setTotal(Array.isArray(data) ? data.length : 0);
      if (!preserveTrace) setLastTrace(getLatestTrace());
    } catch (e) { setError(e?.response?.data?.message || e.message); }
    finally { setLoading(false); }
  }, [page, filterOffering, filterStudent, filterFrom, filterTo]);

  useEffect(() => { load(0); }, []);

  const validate = () => {
    const e = {};
    if (!form.enrollmentId) e.enrollmentId = 'Required';
    if (!form.classDate)    e.classDate    = 'Required';
    setFormErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) return;
    setSaving(true); setTriggerError(null); setLastTrace(null);
    try {
      if (editRow) {
        // PATCH status only
        await attendanceApi.updateStatus(editRow.attendanceId, form.status);
      } else {
        await attendanceApi.create({
          enrollmentId: Number(form.enrollmentId),
          classDate:    form.classDate,
          status:       form.status,
          remarks:      form.remarks || null,
        });
      }
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
      await attendanceApi.delete(deleteTarget.attendanceId);
      setLastTrace(getLatestTrace()); setDeleteTarget(null); load(page, true);
    } catch (e) {
      const msg = e?.response?.data?.message || e.message;
      if (e?.response?.status === 409) setTriggerError(msg); else setError(msg);
      setLastTrace(getLatestTrace()); setDeleteTarget(null);
    } finally { setDeleting(false); }
  };

  const openCreate = () => { setForm(EMPTY_FORM); setFormErrors({}); setEditRow(null); setFormOpen(true); setTriggerError(null); };
  const openEdit   = (row) => {
    setForm({ ...EMPTY_FORM, enrollmentId: row.enrollmentId, classDate: row.classDate, status: row.status, remarks: row.remarks || '' });
    setFormErrors({}); setEditRow(row); setFormOpen(true); setTriggerError(null);
  };

  return (
    <PageShell title="Attendance" subtitle="Record and update class attendance"
      actions={<button className="btn btn-primary" onClick={openCreate}>+ Record Attendance</button>}>
      <ErrorAlert   message={error}        onClose={() => setError(null)} />
      <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />

      <div className="filter-row">
        <Input value={filterOffering} onChange={setFilterOffering} placeholder="Offering ID" type="number" />
        <Input value={filterStudent}  onChange={setFilterStudent}  placeholder="Student ID"  type="number" />
        <Input value={filterFrom} onChange={setFilterFrom} type="date" />
        <span className="filter-sep">to</span>
        <Input value={filterTo} onChange={setFilterTo} type="date" />
        <button className="btn btn-primary" onClick={() => { setPage(0); load(0); }}>🔍 Filter</button>
      </div>

      <DataTable columns={COLUMNS} rows={rows} loading={loading} onEdit={openEdit} onDelete={r => setDeleteTarget(r)} />
      <Pagination page={page} pageSize={PAGE_SIZE} total={total} hasNext={rows.length === PAGE_SIZE}
        onPage={p => { setPage(p); load(p); }} />
      {lastTrace && <InlineTrace trace={lastTrace} />}

      <FormModal open={formOpen} title={editRow ? `Update Attendance #${editRow.attendanceId}` : 'Record Attendance'}
        onClose={() => setFormOpen(false)} onSubmit={handleSave} loading={saving}>
        <TriggerAlert message={triggerError} onClose={() => setTriggerError(null)} />
        {!editRow && (
          <div className="form-row">
            <FormField label="Enrollment ID" required error={formErrors.enrollmentId}>
              <Input type="number" value={form.enrollmentId} onChange={v => setForm(f => ({ ...f, enrollmentId: v }))} />
            </FormField>
            <FormField label="Class Date" required error={formErrors.classDate}>
              <Input type="date" value={form.classDate} onChange={v => setForm(f => ({ ...f, classDate: v }))} />
            </FormField>
          </div>
        )}
        {editRow && (
          <p className="form-hint">
            Editing attendance #{editRow.attendanceId} for {editRow.studentName || `student #${editRow.studentId}`} on {editRow.classDate}. Only status can be changed.
          </p>
        )}
        <div className="form-row">
          <FormField label="Status">
            <Select value={form.status} onChange={v => setForm(f => ({ ...f, status: v }))} options={STATUS_OPTIONS} placeholder="" />
          </FormField>
          {!editRow && (
            <FormField label="Remarks">
              <Input value={form.remarks} onChange={v => setForm(f => ({ ...f, remarks: v }))} placeholder="Optional note" />
            </FormField>
          )}
        </div>
      </FormModal>

      <ConfirmDialog open={!!deleteTarget} title="Delete Attendance Record"
        message={`Delete attendance record #${deleteTarget?.attendanceId} (${deleteTarget?.studentName || 'student'} on ${deleteTarget?.classDate})?`}
        onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={deleting} />
    </PageShell>
  );
}
