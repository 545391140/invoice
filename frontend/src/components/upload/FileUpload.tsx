import React, { useState, useCallback } from 'react';
import { Upload, Button, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import invoiceService from '../../services/invoiceService';

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
    setLoading(true);
    try {
      let response;
      if (async) {
        response = await invoiceService.recognizeAndCropAsync(
          file,
          cropPadding,
          outputFormat
        );
        message.success('任务已提交，请前往任务管理查看进度');
      } else {
        response = await invoiceService.recognizeAndCrop(
          file,
          cropPadding,
          outputFormat
        );
        message.success(`识别完成，共识别到 ${response.data.totalInvoices} 张发票`);
      }
      
      onSuccess?.(response);
      setFileList([]);
    } catch (error: any) {
      // 优先使用 API 错误信息
      const errorMsg = error.apiError?.message || 
                      error.response?.data?.message || 
                      error.message || 
                      '上传失败';
      console.error('上传失败详情:', error);
      message.error(`上传失败: ${errorMsg}`);
      onError?.(error);
    } finally {
      setLoading(false);
    }
  }, [cropPadding, outputFormat, async, onSuccess, onError]);

  const beforeUpload = (file: File) => {
    // 文件格式验证
    const isValidType = ['application/pdf', 'image/jpeg', 'image/png', 'image/jpg'].includes(file.type);
    if (!isValidType) {
      message.error('只支持 PDF、JPG、PNG 格式的文件');
      return false;
    }

    // 文件大小验证（50MB）
    const isLt50M = file.size / 1024 / 1024 < 50;
    if (!isLt50M) {
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

