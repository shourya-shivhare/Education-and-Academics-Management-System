import axios from 'axios';
import { notifyTrace } from '../store/traceStore';

/**
 * api – configured Axios instance.
 *
 * Base URL is '/api' – proxied to http://localhost:8080 by Vite in dev
 * (see vite.config.js).  In production, configure the reverse proxy.
 *
 * Request interceptor:  attaches JWT from localStorage as Bearer token.
 * Response interceptor: unwraps the { data, trace } envelope that the
 *                       Spring Boot backend sends for every response.
 *
 * DBMS concept: the trace array (sql, params, rowsAffected, timeMs) shows
 * every parameterized SQL statement the backend ran, demonstrating that
 * no string-concatenation SQL exists in the codebase (Rule 3).
 */
const api = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor: attach JWT ──────────────────────────────────────────
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('edu_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ── Response interceptor: unwrap { data, trace } envelope ────────────────────
api.interceptors.response.use(
  (response) => {
    const body = response.data;

    // Success envelope: { data: <payload>, trace: [...] }
    if (body !== null && typeof body === 'object' && 'trace' in body) {
      // Forward trace to the global store → TraceContext → TracePanel
      notifyTrace(body.trace ?? []);
      // Replace the full envelope with just the payload so callers receive
      // clean data: const { data } = await api.get('/students')
      response.data = body.data;
    }

    return response;
  },
  (error) => {
    const body = error.response?.data;

    // Error envelope: { code, message, trace }
    // Still publish the trace (e.g. the SELECT that ran before auth failed)
    if (body?.trace) {
      notifyTrace(body.trace);
    }

    // 401 Unauthorized → token expired or invalid → force re-login
    if (error.response?.status === 401) {
      localStorage.removeItem('edu_token');
      localStorage.removeItem('edu_user');
      // Avoid redirect loop if already on /login
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);

export default api;
