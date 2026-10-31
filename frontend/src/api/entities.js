import api from './axios';

// ── Students ─────────────────────────────────────────────────────────────────
export const studentsApi = {
  search: (params) => api.get('/api/students', { params }),
  getById: (id) => api.get(`/api/students/${id}`),
  create: (data) => api.post('/api/students', data),
  update: (id, data) => api.put(`/api/students/${id}`, data),
  delete: (id) => api.delete(`/api/students/${id}`),
};

// ── Faculty ───────────────────────────────────────────────────────────────────
export const facultyApi = {
  search: (params) => api.get('/api/faculty', { params }),
  getById: (id) => api.get(`/api/faculty/${id}`),
  create: (data) => api.post('/api/faculty', data),
  update: (id, data) => api.put(`/api/faculty/${id}`, data),
  delete: (id) => api.delete(`/api/faculty/${id}`),
};

// ── Subjects ──────────────────────────────────────────────────────────────────
export const subjectsApi = {
  search: (params) => api.get('/api/subjects', { params }),
  getById: (id) => api.get(`/api/subjects/${id}`),
  create: (data) => api.post('/api/subjects', data),
  update: (id, data) => api.put(`/api/subjects/${id}`, data),
  delete: (id) => api.delete(`/api/subjects/${id}`),
};

// ── Course Offerings ──────────────────────────────────────────────────────────
export const offeringsApi = {
  search: (params) => api.get('/api/offerings', { params }),
  getById: (id) => api.get(`/api/offerings/${id}`),
  create: (data) => api.post('/api/offerings', data),
  update: (id, data) => api.put(`/api/offerings/${id}`, data),
  delete: (id) => api.delete(`/api/offerings/${id}`),
};

// ── Enrollment ────────────────────────────────────────────────────────────────
export const enrollmentApi = {
  search: (params) => api.get('/api/enrollment', { params }),
  getById: (id) => api.get(`/api/enrollment/${id}`),
  create: (data) => api.post('/api/enrollment', data),
  update: (id, data) => api.put(`/api/enrollment/${id}`, data),
  delete: (id) => api.delete(`/api/enrollment/${id}`),
};

// ── Attendance ────────────────────────────────────────────────────────────────
export const attendanceApi = {
  search: (params) => api.get('/api/attendance', { params }),
  getById: (id) => api.get(`/api/attendance/${id}`),
  create: (data) => api.post('/api/attendance', data),
  updateStatus: (id, status) => api.patch(`/api/attendance/${id}`, { status }),
  delete: (id) => api.delete(`/api/attendance/${id}`),
};

// ── Marks ─────────────────────────────────────────────────────────────────────
export const marksApi = {
  search: (params) => api.get('/api/marks', { params }),
  getByPk: (enrollmentId, offeringId, assessmentNo) =>
    api.get('/api/marks/entry', { params: { enrollmentId, offeringId, assessmentNo } }),
  upsert: (data) => api.post('/api/marks', data),
};
