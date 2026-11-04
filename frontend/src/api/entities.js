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
