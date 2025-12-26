import React, { useState, useCallback } from 'react';
import { Upload, Button, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import invoiceService from '../../services/invoiceService';
import { saveTask } from '../../utils/taskStorage';

interface FileUploadProps {
  onSuccess?: (response: any) => void;
  onError?: (error: any) => void;
  cropPadding?: number;
  outputFormat?: string;
  async?: boolean;
}

const FileUpload: React.FC<FileUploadProps> = ({
  onSuccess,
  onError,
  cropPadding = 10,
  outputFormat = 'jpg',
  async = false,
}) => {
  const [loading, setLoading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const handleUpload = useCallback(async (file: File) => {
    if (loading) {
      console.warn('已有任务正在处理中，忽略本次请求');
      return;
    }
    
    console.log('开始处理文件上传:', file.name, '模式:', async ? '异步' : '同步');
    setLoading(true);
    try {
      let response;
      if (async) {
        console.log('发起异步识别请求...');
        response = await invoiceService.recognizeAndCropAsync(
          file,
          cropPadding,
          outputFormat
        );
        console.log('异步识别请求已提交:', response);
        // 保存异步任务到localStorage
        if (response?.data?.taskId) {
          saveTask({
            taskId: response.data.taskId,
            status: 'PROCESSING',
            createdAt: new Date().toISOString(),
            filename: file.name,
          });
        }
        message.success('任务已提交，请前往任务列表查看进度');
      } else {
        console.log('发起同步识别请求...');
        response = await invoiceService.recognizeAndCrop(
          file,
          cropPadding,
          outputFormat
        );
        console.log('同步识别请求已完成:', response);
        // 响应拦截器已经返回了 response.data，所以这里 response 就是 ApiResponse
        const invoiceData = response?.data;
        if (invoiceData?.totalInvoices !== undefined) {
          if (invoiceData.totalInvoices > 0) {
            // HomePage 已经会提示成功，这里可以不再重复
          } else {
            // HomePage 会处理 0 张的情况
          }
        }
      }
      
      console.log('回调 onSuccess...');
      onSuccess?.(response);
      setFileList([]);
    } catch (error: any) {
      // 优先使用 API 错误信息
      const errorMsg = error.apiError?.message || 
                      error.response?.data?.message || 
                      error.message || 
                      '上传失败';
      console.error('上传处理失败:', error);
      message.error(`上传失败: ${errorMsg}`);
      onError?.(error);
    } finally {
      console.log('处理结束，重置 loading 状态');
      setLoading(false);
    }
  }, [cropPadding, outputFormat, async, onSuccess, onError, loading]);

  const beforeUpload = (file: File) => {
    console.log('beforeUpload 触发:', file.name);
    // 文件格式验证 - 检查 MIME 类型和文件扩展名
    const validMimeTypes = ['application/pdf', 'image/jpeg', 'image/png', 'image/jpg'];
    const validExtensions = ['.pdf', '.jpg', '.jpeg', '.png'];
    const fileExtension = '.' + file.name.split('.').pop()?.toLowerCase();
    
    const isValidType = validMimeTypes.includes(file.type) || 
                       validExtensions.includes(fileExtension);
    
    if (!isValidType) {
      console.warn('文件格式不支持:', file.name, file.type);
      message.error(`不支持的文件格式：${file.name}。只支持 PDF、JPG、PNG 格式的文件`);
      return false;
    }

    // 文件大小验证（50MB）
    const isLt50M = file.size / 1024 / 1024 < 50;
    if (!isLt50M) {
      console.warn('文件太大:', file.name, file.size);
      message.error('文件大小不能超过 50MB');
      return false;
    }

    handleUpload(file);
    return false; // 阻止自动上传
  };

  return (
    <Upload
      fileList={fileList}
      beforeUpload={beforeUpload}
      onChange={({ fileList: newFileList }) => setFileList(newFileList)}
      accept=".pdf,.jpg,.jpeg,.png"
      maxCount={1}
    >
      <Button icon={<UploadOutlined />} loading={loading}>
        选择文件
      </Button>
    </Upload>
  );
};

export default FileUpload;

