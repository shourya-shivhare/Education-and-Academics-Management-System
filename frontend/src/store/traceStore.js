/**
 * traceStore.js – module-level callback bridge.
 *
 * Problem: the Axios interceptor runs outside React's tree, so it cannot
 * call React state setters directly.  We solve this with a thin module-level
 * registry:
 *
 *   1. TraceContext registers a setter via registerTraceCallback() on mount.
 *   2. The Axios interceptor calls notifyTrace() on every response.
 *   3. notifyTrace forwards the trace array to the registered setter, updating
 *      global React state so every TracePanel in the tree re-renders.
 *
 * This is simpler than a pub/sub library and avoids adding Redux/Zustand.
 */

/** @type {((trace: object[]) => void) | null} */
let _callback = null;
let _latestTrace = [];

/**
 * Register the React state-setter from TraceContext.
 * Called once when TraceProvider mounts; cleared on unmount.
 *
 * @param {((trace: object[]) => void) | null} fn
 */
export function registerTraceCallback(fn) {
  _callback = fn;
}

/**
 * Publish a new trace array.  Called by the Axios response interceptor.
 *
 * @param {object[]} trace – array of { sql, params, rowsAffected, timeMs }
 */
export function notifyTrace(trace) {
  _latestTrace = Array.isArray(trace) ? trace : [];
  if (_callback) {
    _callback(_latestTrace);
  }
}

export function getLatestTrace() {
  return _latestTrace;
}
