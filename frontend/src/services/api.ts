import axios from 'axios';
import type { ApiResponse } from '../types/api';

// API 基础配置
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 
  'http://localhost:8080/api/v1/invoice';

// 创建 axios 实例
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000, // 60秒超时
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    // 可以在这里添加 token 等认证信息
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    // 统一错误处理
    if (error.response) {
      const { code, message } = error.response.data || {};
      console.error('API 错误:', code, message);
    } else {
      console.error('网络错误:', error.message);
    }
    return Promise.reject(error);
  }
);

export default apiClient;

