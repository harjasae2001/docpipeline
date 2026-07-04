import axios from 'axios';

const apiClient = axios.create({
  // VITE_API_BASE_URL can be set in frontend/.env or frontend/.env.local
  // Default: http://localhost:8080/api  (works for both local and dockerised backend,
  //          since port 8080 is always mapped to the host)
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor – attach Bearer token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor – handle 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ─── Auth ────────────────────────────────────────────────────────────────────

export function login(email, password) {
  return apiClient.post('/auth/login', { email, password });
}

export function register(email, password, fullName) {
  return apiClient.post('/auth/register', { email, password, fullName });
}

// ─── Documents ───────────────────────────────────────────────────────────────

export function getPresignedUrl(fileName, contentType) {
  return apiClient.post('/documents/presigned-url', { fileName, contentType });
}

export function confirmUpload(documentId) {
  return apiClient.post(`/documents/${documentId}/confirm-upload`);
}

export function listDocuments(page = 0, size = 10) {
  return apiClient.get('/documents', { params: { page, size } });
}

export function getDocument(id) {
  return apiClient.get(`/documents/${id}`);
}

export function getDownloadUrl(id) {
  return apiClient.get(`/documents/${id}/download-url`);
}

export function deleteDocument(id) {
  return apiClient.delete(`/documents/${id}`);
}

// ─── Reports ─────────────────────────────────────────────────────────────────

export function generateReport(documentId) {
  return apiClient.post(`/reports/${documentId}/generate`);
}

export function getReportDownloadUrl(documentId) {
  return apiClient.get(`/reports/${documentId}/download-url`);
}

// ─── S3 Direct Upload ────────────────────────────────────────────────────────

export function uploadToS3(presignedUrl, file, contentType, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', presignedUrl, true);
    xhr.setRequestHeader('Content-Type', contentType);

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        const percent = Math.round((event.loaded / event.total) * 100);
        onProgress(percent);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr);
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`));
      }
    });

    xhr.addEventListener('error', () => reject(new Error('Upload failed')));
    xhr.addEventListener('abort', () => reject(new Error('Upload aborted')));

    xhr.send(file);
  });
}

export default apiClient;
