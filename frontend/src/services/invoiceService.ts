import apiClient from './api';
import type { 
  ApiResponse, 
  InvoiceRecognizeResponse, 
  InvoiceInfo,
  AsyncTaskResponse,
  TaskStatusResponse,
  HealthResponse
} from '../types/api';

class InvoiceService {
  /**
   * 同步识别与裁切
   */
  async recognizeAndCrop(
    file: File,
    cropPadding: number = 10,
    outputFormat: string = 'jpg'
  ): Promise<ApiResponse<InvoiceRecognizeResponse>> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('cropPadding', cropPadding.toString());
    formData.append('outputFormat', outputFormat);

    return apiClient.post('/recognize-and-crop', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  /**
   * 异步识别与裁切
   */
  async recognizeAndCropAsync(
    file: File,
    cropPadding: number = 10,
    outputFormat: string = 'jpg'
  ): Promise<ApiResponse<AsyncTaskResponse>> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('cropPadding', cropPadding.toString());
    formData.append('outputFormat', outputFormat);

    return apiClient.post('/recognize-and-crop/async', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  /**
   * 查询任务状态
   */
  async getTaskStatus(taskId: string): Promise<ApiResponse<TaskStatusResponse>> {
    return apiClient.get(`/task/${taskId}`);
  }

  /**
   * 获取裁切后图片预览URL
   */
  getCroppedImagePreviewUrl(filename: string): string {
    return `${apiClient.defaults.baseURL}/preview/cropped/${filename}`;
  }

  /**
   * 获取裁切后图片下载URL
   */
  getCroppedImageDownloadUrl(filename: string): string {
    return `${apiClient.defaults.baseURL}/download/${filename}`;
  }

  /**
   * 获取原始图片预览URL
   */
  getOriginalImagePreviewUrl(taskId: string, page: number = 1): string {
    return `${apiClient.defaults.baseURL}/preview/original/${taskId}?page=${page}`;
  }

  /**
   * 获取原始图片下载URL
   */
  getOriginalImageDownloadUrl(taskId: string, page: number = 1): string {
    return `${apiClient.defaults.baseURL}/download/original/${taskId}?page=${page}`;
  }

  /**
   * 健康检查
   */
  async healthCheck(): Promise<ApiResponse<HealthResponse>> {
    return apiClient.get('/health');
  }
}

export default new InvoiceService();

