import { createContext, useContext, useEffect, useState } from 'react';
import { registerTraceCallback } from './traceStore';

/**
 * TraceContext – global store for the latest SQL trace.
 *
 * Every API response from the backend contains a "trace" array:
 *   { sql, params, rowsAffected, timeMs }
 *
 * The Axios interceptor (api/axios.js) calls notifyTrace() with that array.
 * This context receives it via the module-level callback registered in
 * traceStore.js, and updates React state so all <TracePanel> components
 * in the tree can re-render.
 *
 * Usage:
 *   const { trace, clearTrace } = useTrace();
 */

const TraceContext = createContext({
  trace: [],
  clearTrace: () => {},
});

export function TraceProvider({ children }) {
  const [trace, setTrace] = useState([]);

  // Register setTrace as the global callback when this component mounts.
  // The Axios interceptor will call notifyTrace() → setTrace() on every response.
  useEffect(() => {
    registerTraceCallback(setTrace);
    return () => registerTraceCallback(null); // cleanup on unmount
  }, []);

  const clearTrace = () => setTrace([]);

  return (
    <TraceContext.Provider value={{ trace, clearTrace }}>
      {children}
    </TraceContext.Provider>
  );
}

/**
 * useTrace – hook to read the latest trace array in any component.
 * @returns {{ trace: object[], clearTrace: () => void }}
 */
export function useTrace() {
  return useContext(TraceContext);
}
