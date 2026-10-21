import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL;
const api = axios.create({ baseURL, timeout: 60000 });

function requireApiBaseUrl() {
  if (!baseURL) throw new Error('VITE_API_BASE_URL is not configured. Set it to the deployed backend URL.');
}

export async function uploadFinancialReport(file) {
  requireApiBaseUrl();
  const form = new FormData();
  form.append('file', file);
  const { data } = await api.post('/api/analysis/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export async function getHealth() {
  requireApiBaseUrl();
  const { data } = await api.get('/api/analysis/health');
  return data;
}
