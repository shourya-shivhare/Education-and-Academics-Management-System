import { useState, useCallback } from 'react';
import { setTraceCallback } from '../store/traceStore';

/**
 * useCrud – generic hook that drives every entity page.
 *
 * Manages:
 *   - rows, loading, error, pagination state
 *   - lastTrace  – the trace from the most recent API call (shown inline)
 *   - triggerError – 409 messages from the DB (trigger violations)
 *   - conflictError – optimistic-lock 409 (marks only)
 *
 * Usage:
 *   const crud = useCrud(api.search);
 *   crud.load({ name: 'Alice', page: 0, size: 20 })
 *   await crud.run(() => api.create(data))
 */
export default function useCrud(searchFn) {
  const [rows, setRows]             = useState([]);
  const [loading, setLoading]       = useState(false);
  const [error, setError]           = useState(null);
  const [triggerError, setTriggerError] = useState(null);   // 409 DB trigger
  const [conflictError, setConflictError] = useState(null); // 409 optimistic lock
  const [lastTrace, setLastTrace]   = useState([]);
  const [page, setPage]             = useState(0);
  const [pageSize]                  = useState(20);
  const [totalRows, setTotalRows]   = useState(0);

  // Intercept trace for inline display
  const captureTrace = useCallback((trace) => {
    setLastTrace(trace || []);
  }, []);

  const load = useCallback(async (filters = {}) => {
    setLoading(true);
    setError(null);
    setTriggerError(null);
    setLastTrace([]);

    // Temporarily override the global trace callback to also set local trace
    const prev = window.__traceCallback;
    window.__traceCallback = captureTrace;

    try {
      const data = await searchFn({ ...filters, page, size: pageSize });
      setRows(Array.isArray(data) ? data : data?.content ?? data ?? []);
      setTotalRows(Array.isArray(data) ? data.length : data?.totalElements ?? data?.length ?? 0);
    } catch (e) {
      setError(e?.response?.data?.message || e.message || 'Failed to load');
    } finally {
      setLoading(false);
      window.__traceCallback = prev;
    }
  }, [searchFn, page, pageSize, captureTrace]);

  /**
   * run – wraps any mutating API call, captures trace, normalises errors.
   * Returns { ok: true, data } or { ok: false }.
   */
  const run = useCallback(async (apiFn, { optimisticLock = false } = {}) => {
    setLoading(true);
    setError(null);
    setTriggerError(null);
    setConflictError(null);
    setLastTrace([]);

    try {
      const result = await apiFn();
      // Trace is set by the Axios interceptor → traceStore → our listener
      return { ok: true, data: result };
    } catch (e) {
      const status = e?.response?.status;
      const msg    = e?.response?.data?.message || e.message || 'Error';

      if (status === 409 && optimisticLock) {
        setConflictError(msg);
      } else if (status === 409) {
        setTriggerError(msg);
      } else {
        setError(msg);
      }
      return { ok: false };
    } finally {
      setLoading(false);
    }
  }, []);

  const clearErrors = () => {
    setError(null);
    setTriggerError(null);
    setConflictError(null);
  };

  return {
    rows, setRows,
    loading,
    error, setError,
    triggerError, setTriggerError,
    conflictError, setConflictError,
    lastTrace, setLastTrace,
    page, setPage,
    pageSize,
    totalRows,
    load,
    run,
    clearErrors,
  };
}
