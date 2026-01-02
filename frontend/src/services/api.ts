import axios from 'axios';

// API 基础配置
// 使用相对路径以利用 Vite 代理，避免 CORS 问题
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 
  '/api/v1/invoice';

// 创建 axios 实例
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 300000, // 300秒（5分钟）超时（API调用可能需要较长时间，特别是处理大文件或多页PDF时，实际处理时间约140秒）
  // 不设置默认 Content-Type，让 axios 根据数据类型自动设置
});

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    // 如果数据是 FormData，不设置 Content-Type，让浏览器自动设置（包含 boundary）
    if (config.data instanceof FormData) {
      // 明确删除 Content-Type，让浏览器自动设置 multipart/form-data; boundary=...
      delete config.headers['Content-Type'];
      delete config.headers['content-type'];
    } else if (config.data && typeof config.data === 'object' && !config.headers['Content-Type']) {
      // 非 FormData 的 JSON 请求，设置 Content-Type
      config.headers['Content-Type'] = 'application/json';
    }
    
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
      const status = error.response.status;
      const statusText = error.response.statusText;
      console.error('API 错误:', {
        status,
        statusText,
        code,
        message,
        data: error.response.data,
        headers: error.response.headers
      });
      // 将完整的错误信息附加到 error 对象上，方便组件使用
      error.apiError = {
        status,
        statusText,
        code,
        message: message || statusText || '请求失败'
      };
    } else if (error.request) {
      console.error('网络错误: 请求已发出但没有收到响应', error.request);
      // 检查是否是超时错误
      if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        error.apiError = {
          message: '请求超时：处理时间过长，请尝试使用异步模式或稍后重试'
        };
      } else {
        error.apiError = {
          message: '网络错误：无法连接到服务器'
        };
      }
    } else {
      console.error('请求配置错误:', error.message);
      error.apiError = {
        message: error.message || '请求配置错误'
      };
    }
    return Promise.reject(error);
  }
);

export default apiClient;


