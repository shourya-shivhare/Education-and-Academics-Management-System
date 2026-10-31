/**
 * Shared building blocks used by every entity page.
 *
 * Components exported:
 *   PageShell        – page title + action bar
 *   SearchBar        – controlled text input with search button
 *   DataTable        – paginated table with edit/delete buttons
 *   ConfirmDialog    – modal for delete confirmation
 *   TriggerAlert     – red alert quoting DB trigger errors (409)
 *   ConflictAlert    – optimistic-lock conflict banner (marks page)
 *   InlineTrace      – compact TracePanel shown below each action
 *   FormModal        – Bootstrap modal wrapper for insert/edit forms
 *   Pagination       – prev/next page controls
 */

import React from 'react';
import TracePanel from './TracePanel';

// ── PageShell ─────────────────────────────────────────────────────────────────
export function PageShell({ title, subtitle, actions, children }) {
  return (
    <div className="entity-page">
      <div className="entity-header">
        <div>
          <h1 className="entity-title">{title}</h1>
          {subtitle && <p className="entity-subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="entity-actions">{actions}</div>}
      </div>
      {children}
    </div>
  );
}

// ── SearchBar ─────────────────────────────────────────────────────────────────
export function SearchBar({ value, onChange, onSearch, placeholder = 'Search…', children }) {
  return (
    <div className="search-bar">
      <input
        type="text"
        className="search-input"
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        onKeyDown={e => e.key === 'Enter' && onSearch()}
      />
      {children}
      <button className="btn btn-primary" onClick={onSearch}>
        🔍 Search
      </button>
    </div>
  );
}

// ── DataTable ─────────────────────────────────────────────────────────────────
export function DataTable({ columns, rows, onEdit, onDelete, loading, emptyMsg = 'No records found.' }) {
  if (loading) {
    return (
      <div className="table-loading">
        <div className="spinner" />
        <span>Loading…</span>
      </div>
    );
  }

  return (
    <div className="table-wrapper">
      <table className="entity-table">
        <thead>
          <tr>
            {columns.map(col => (
              <th key={col.key}>{col.label}</th>
            ))}
            {(onEdit || onDelete) && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length + 1} className="empty-row">{emptyMsg}</td>
            </tr>
          ) : (
            rows.map((row, i) => (
              <tr key={i} className="entity-row">
                {columns.map(col => (
                  <td key={col.key}>
                    {col.render ? col.render(row) : (row[col.key] ?? '—')}
                  </td>
                ))}
                {(onEdit || onDelete) && (
                  <td className="action-cell">
                    {onEdit && (
                      <button className="btn btn-sm btn-outline" onClick={() => onEdit(row)}>
                        ✏️ Edit
                      </button>
                    )}
                    {onDelete && (
                      <button className="btn btn-sm btn-danger-outline" onClick={() => onDelete(row)}>
                        🗑️ Delete
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

// ── Pagination ────────────────────────────────────────────────────────────────
export function Pagination({ page, pageSize, total, onPage }) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <div className="pagination-bar">
      <span className="pagination-info">
        Page {page + 1} of {totalPages} ({total} rows)
      </span>
      <div className="pagination-btns">
        <button className="btn btn-sm btn-outline" disabled={page === 0} onClick={() => onPage(page - 1)}>
          ← Prev
        </button>
        <button className="btn btn-sm btn-outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next →
        </button>
      </div>
    </div>
  );
}

// ── ConfirmDialog ─────────────────────────────────────────────────────────────
export function ConfirmDialog({ open, title, message, onConfirm, onCancel, loading }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal-box confirm-box" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <span className="modal-icon">⚠️</span>
          <h3>{title || 'Confirm Delete'}</h3>
        </div>
        <p className="confirm-msg">{message}</p>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onCancel} disabled={loading}>Cancel</button>
          <button className="btn btn-danger" onClick={onConfirm} disabled={loading}>
            {loading ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── FormModal ─────────────────────────────────────────────────────────────────
export function FormModal({ open, title, onClose, onSubmit, loading, children, submitLabel = 'Save' }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-box form-box" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {children}
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={loading}>Cancel</button>
          <button className="btn btn-primary" onClick={onSubmit} disabled={loading}>
            {loading ? 'Saving…' : submitLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── TriggerAlert ──────────────────────────────────────────────────────────────
export function TriggerAlert({ message, onClose }) {
  if (!message) return null;
  return (
    <div className="alert alert-trigger">
      <div className="alert-icon">🚫</div>
      <div className="alert-content">
        <strong>Database Rule Violation (409)</strong>
        <p className="alert-msg">"{message}"</p>
      </div>
      <button className="alert-close" onClick={onClose}>✕</button>
    </div>
  );
}

// ── ConflictAlert ─────────────────────────────────────────────────────────────
export function ConflictAlert({ message, onReload, onClose }) {
  if (!message) return null;
  return (
    <div className="alert alert-conflict">
      <div className="alert-icon">🔄</div>
      <div className="alert-content">
        <strong>Optimistic Lock Conflict (409)</strong>
        <p className="alert-msg">
          Another user modified this record. Please reload to get the latest version before saving.
        </p>
        {message && <code className="alert-detail">{message}</code>}
      </div>
      <div className="alert-btns">
        <button className="btn btn-sm btn-primary" onClick={onReload}>↻ Reload</button>
        <button className="alert-close" onClick={onClose}>✕</button>
      </div>
    </div>
  );
}

// ── ErrorAlert ────────────────────────────────────────────────────────────────
export function ErrorAlert({ message, onClose }) {
  if (!message) return null;
  return (
    <div className="alert alert-error">
      <div className="alert-icon">❌</div>
      <div className="alert-content">
        <strong>Error</strong>
        <p className="alert-msg">{message}</p>
      </div>
      <button className="alert-close" onClick={onClose}>✕</button>
    </div>
  );
}

// ── InlineTrace ───────────────────────────────────────────────────────────────
export function InlineTrace({ trace }) {
  if (!trace || trace.length === 0) return null;
  return (
    <div className="inline-trace">
      <div className="inline-trace-header">
        <span className="trace-badge">{trace.length} SQL</span>
        <span className="trace-label">Query Trace</span>
      </div>
      <TracePanel trace={trace} inline />
    </div>
  );
}

// ── FormField ─────────────────────────────────────────────────────────────────
export function FormField({ label, required, error, children }) {
  return (
    <div className="form-field">
      <label className="field-label">
        {label}{required && <span className="required">*</span>}
      </label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

// ── Select ────────────────────────────────────────────────────────────────────
export function Select({ value, onChange, options, placeholder = 'Select…', disabled }) {
  return (
    <select className="form-control" value={value} onChange={e => onChange(e.target.value)} disabled={disabled}>
      {placeholder && <option value="">{placeholder}</option>}
      {options.map(opt => (
        <option key={opt.value} value={opt.value}>{opt.label}</option>
      ))}
    </select>
  );
}

// ── Input ─────────────────────────────────────────────────────────────────────
export function Input({ type = 'text', value, onChange, placeholder, disabled, min, max, step }) {
  return (
    <input
      type={type}
      className="form-control"
      value={value ?? ''}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      disabled={disabled}
      min={min}
      max={max}
      step={step}
    />
  );
}
