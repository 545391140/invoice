export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

export interface InvoiceInfo {
  index: number;
  page: number;
  bbox: number[];
  confidence: number;
  imageUrl?: string;           // 裁切后图片预览URL
  downloadUrl?: string;       // 裁切后图片下载URL
  originalImageUrl?: string;  // 原始图片预览URL
  filename: string;
}

export interface InvoiceRecognizeResponse {
  taskId: string;
  totalInvoices: number;
  invoices: InvoiceInfo[];
  processingTime: number;
}

export interface AsyncTaskResponse {
  taskId: string;
  status: string;
  estimatedTime?: number;
}

export interface TaskStatusResponse {
  taskId: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  progress: number;
  currentPage?: number;
  totalPages?: number;
  statusMessage?: string;
  totalInvoices?: number;
  invoices?: InvoiceInfo[];
  createdAt: string;
  completedAt?: string;
}

export interface HealthResponse {
  status: string;
  version: string;
  uptime?: string;
}

