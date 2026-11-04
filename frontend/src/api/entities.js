import api from './axios';

// ── Students ─────────────────────────────────────────────────────────────────
// baseURL is '/api' (set in axios.js) – paths here must NOT include /api again.
export const studentsApi = {
  search: (params) => api.get('/students', { params }),
  getById: (id) => api.get(`/students/${id}`),
  create: (data) => api.post('/students', data),
  update: (id, data) => api.put(`/students/${id}`, data),
  delete: (id) => api.delete(`/students/${id}`),
};

// ── Faculty ───────────────────────────────────────────────────────────────────
export const facultyApi = {
  search: (params) => api.get('/faculty', { params }),
  getById: (id) => api.get(`/faculty/${id}`),
  create: (data) => api.post('/faculty', data),
  update: (id, data) => api.put(`/faculty/${id}`, data),
  delete: (id) => api.delete(`/faculty/${id}`),
};

// ── Subjects ──────────────────────────────────────────────────────────────────
export const subjectsApi = {
  search: (params) => api.get('/subjects', { params }),
  getById: (id) => api.get(`/subjects/${id}`),
  create: (data) => api.post('/subjects', data),
  update: (id, data) => api.put(`/subjects/${id}`, data),
  delete: (id) => api.delete(`/subjects/${id}`),
};

// ── Course Offerings ──────────────────────────────────────────────────────────
export const offeringsApi = {
  search: (params) => api.get('/offerings', { params }),
  getById: (id) => api.get(`/offerings/${id}`),
  create: (data) => api.post('/offerings', data),
  update: (id, data) => api.put(`/offerings/${id}`, data),
  delete: (id) => api.delete(`/offerings/${id}`),
};

// ── Enrollment ────────────────────────────────────────────────────────────────
// Backend controller is at /api/enrollments (plural)
export const enrollmentApi = {
  search: (params) => api.get('/enrollments', { params }),
  getById: (id) => api.get(`/enrollments/${id}`),
  create: (data) => api.post('/enrollments', data),
  update: (id, data) => api.put(`/enrollments/${id}`, data),
  drop:   (data) => api.delete('/enrollments', { data }),  // uses sp_drop_enrollment
  delete: (id) => api.delete(`/enrollments/${id}`),
};

// ── Attendance ────────────────────────────────────────────────────────────────
export const attendanceApi = {
  search: (params) => api.get('/attendance', { params }),
  getById: (id) => api.get(`/attendance/${id}`),
  create: (data) => api.post('/attendance', data),
  updateStatus: (id, status) => api.patch(`/attendance/${id}`, { status }),
  delete: (id) => api.delete(`/attendance/${id}`),
};

// ── Marks ─────────────────────────────────────────────────────────────────────
export const marksApi = {
  search: (params) => api.get('/marks', { params }),
  getByPk: (enrollmentId, offeringId, assessmentNo) =>
    api.get('/marks/entry', { params: { enrollmentId, offeringId, assessmentNo } }),
  upsert: (data) => api.post('/marks', data),
  delete: (data) => api.delete('/marks', { params: data }),
};

export const reportsApi = {
  list: () => api.get('/reports'),
  run: (id, params) => api.post(`/reports/${id}/run`, { params }),
  csv: (id, params) => api.post(`/reports/${id}/run?format=csv`, { params }, { responseType: 'blob' }),
  toppers: (topN = 3) => api.get('/reports/toppers', { params: { topN } }),
  departmentPerformance: (departmentId) =>
    api.get('/reports/department-performance', { params: departmentId ? { departmentId } : {} }),
};

export const schemaApi = {
  get: (table) => api.get('/schema', { params: table ? { table } : {} }),
  erImage: () => api.get('/schema/er-image', { responseType: 'blob' }),
};

export const labsApi = {
  atomicity: (forceFailure = false) => api.post('/labs/acid/atomicity', null, { params: { forceFailure } }),
  consistency: () => api.post('/labs/acid/consistency'),
  isolation: (level = 'REPEATABLE_READ') => api.post('/labs/acid/isolation', null, { params: { level } }),
  durability: () => api.post('/labs/acid/durability'),
  durabilityVerify: () => api.get('/labs/acid/durability/verify'),
  lastSeatRace: (safe = false) => api.post('/labs/concurrency/last-seat-race', null, { params: { safe } }),
  lostUpdate: (optimistic = false) => api.post('/labs/concurrency/lost-update', null, { params: { optimistic } }),
  deadlock: () => api.post('/labs/concurrency/deadlock'),
};

export const consoleApi = {
  select: (sql, params = []) => api.post('/console/select', { sql, params }),
};

export const riskApi = {
  predict: (data) => api.post('/risk/predict', data),
};
