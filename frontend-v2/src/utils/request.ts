import axios from 'axios';

const request = axios.create({
  baseURL: '/api', // Vite proxy configured in vite.config.ts
  timeout: 10000,
});

// Response interceptor
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    console.error('Request Error:', error);
    return Promise.reject(error);
  }
);

export default request;
