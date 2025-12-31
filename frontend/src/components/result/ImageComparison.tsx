import React, { useState } from 'react';
import { Card, Image, Tabs, Button, Descriptions } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { InvoiceInfo } from '../../types/api';
import invoiceService from '../../services/invoiceService';

interface ImageComparisonProps {
  invoice: InvoiceInfo;
  taskId?: string;
}

const ImageComparison: React.FC<ImageComparisonProps> = ({ invoice, taskId }) => {
  const [activeTab, setActiveTab] = useState<string>('cropped');

  const croppedImageUrl = invoice.imageUrl || 
    invoiceService.getCroppedImagePreviewUrl(invoice.filename);
  
  const originalImageUrl = invoice.originalImageUrl || 
    (taskId ? invoiceService.getOriginalImagePreviewUrl(taskId, invoice.page) : '');

  const tabItems = [
    {
      key: 'cropped',
      label: '裁切后',
      children: (
        <div style={{ textAlign: 'center' }}>
          <Image
            src={croppedImageUrl}
            alt="裁切后的发票"
            style={{ maxWidth: '100%', maxHeight: '600px' }}
            preview={{
              mask: '点击放大',
            }}
          />
          <div style={{ marginTop: 16 }}>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => {
                const url = invoice.downloadUrl || 
                  invoiceService.getCroppedImageDownloadUrl(invoice.filename);
                window.open(url, '_blank');
              }}
            >
              下载裁切后的图片
            </Button>
          </div>
        </div>
      ),
    },
    {
      key: 'original',
      label: '原始图片',
      children: originalImageUrl ? (
        <div style={{ textAlign: 'center', position: 'relative' }}>
          <Image
            src={originalImageUrl}
            alt="原始图片"
            style={{ maxWidth: '100%', maxHeight: '600px' }}
            preview={{
              mask: '点击放大',
            }}
          />
          {/* 在原始图片上标注识别区域 */}
          <div style={{ 
            position: 'absolute',
            border: '3px solid #ff4d4f',
            left: `${invoice.bbox[0]}px`,
            top: `${invoice.bbox[1]}px`,
            width: `${invoice.bbox[2] - invoice.bbox[0]}px`,
            height: `${invoice.bbox[3] - invoice.bbox[1]}px`,
            pointerEvents: 'none',
            boxShadow: '0 0 10px rgba(255, 77, 79, 0.5)'
          }}>
            <div style={{
              position: 'absolute',
              top: -28,
              left: 0,
              background: '#ff4d4f',
              color: 'white',
              padding: '4px 8px',
              fontSize: '12px',
              fontWeight: 'bold',
              borderRadius: '4px'
            }}>
              识别区域
            </div>
          </div>
          <div style={{ marginTop: 16 }}>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => {
                if (taskId) {
                  const url = invoiceService.getOriginalImageDownloadUrl(taskId, invoice.page);
                  window.open(url, '_blank');
                }
              }}
            >
              下载原始图片
            </Button>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          原始图片不可用
        </div>
      ),
    },
    {
      key: 'info',
      label: '详细信息',
      children: (
        <Descriptions bordered column={1}>
          <Descriptions.Item label="发票序号">{invoice.index + 1}</Descriptions.Item>
          <Descriptions.Item label="页码">{invoice.page}</Descriptions.Item>
          <Descriptions.Item label="边界框坐标">
            [{invoice.bbox.join(', ')}]
          </Descriptions.Item>
          <Descriptions.Item label="置信度">
            {(invoice.confidence * 100).toFixed(2)}%
          </Descriptions.Item>
          <Descriptions.Item label="文件名">{invoice.filename}</Descriptions.Item>
        </Descriptions>
      ),
    },
  ];

  return (
    <Card title={`发票 ${invoice.index + 1} - 图片对比`}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
      />
    </Card>
  );
};

export default ImageComparison;

















