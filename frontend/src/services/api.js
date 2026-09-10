import axios from 'axios';
import { supabase } from '../lib/supabase';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor – attach Bearer token
apiClient.interceptors.request.use(
  async (config) => {
    const { data } = await supabase.auth.getSession();
    const token = data.session?.access_token;
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
  async (error) => {
    if (error.response && error.response.status === 401) {
      await supabase.auth.signOut();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

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

// ─── Direct Storage Upload ───────────────────────────────────────────────────

export function uploadToStorage(presignedUrl, file, contentType, onProgress) {
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
