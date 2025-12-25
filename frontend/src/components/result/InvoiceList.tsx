import React, { useState } from 'react';
import { Card, List, Image, Button, Space, Tag, Modal } from 'antd';
import { DownloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { InvoiceInfo } from '../../types/api';
import invoiceService from '../../services/invoiceService';

interface InvoiceListProps {
  invoices: InvoiceInfo[];
  taskId?: string;
}

const InvoiceList: React.FC<InvoiceListProps> = ({ invoices, taskId }) => {
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewImage, setPreviewImage] = useState<string>('');
  const [previewTitle, setPreviewTitle] = useState<string>('');

  const handleDownload = (filename: string, downloadUrl?: string) => {
    const url = downloadUrl || invoiceService.getCroppedImageDownloadUrl(filename);
    window.open(url, '_blank');
  };

  const handlePreview = (imageUrl: string, title: string) => {
    setPreviewImage(imageUrl);
    setPreviewTitle(title);
    setPreviewVisible(true);
  };

  return (
    <>
      <List
        grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4 }}
        dataSource={invoices}
        renderItem={(invoice: InvoiceInfo) => (
          <List.Item>
            <Card
              hoverable
              cover={
                <div style={{ position: 'relative' }}>
                  <Image
                    src={invoice.imageUrl || invoiceService.getCroppedImagePreviewUrl(invoice.filename)}
                    alt={`发票 ${invoice.index + 1}`}
                    preview={false}
                    style={{ width: '100%', height: '200px', objectFit: 'cover' }}
                  />
                  <div style={{
                    position: 'absolute',
                    top: 8,
                    right: 8,
                    display: 'flex',
                    gap: 8
                  }}>
                    <Button
                      type="primary"
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => handlePreview(
                        invoice.imageUrl || invoiceService.getCroppedImagePreviewUrl(invoice.filename),
                        `裁切后发票 ${invoice.index + 1}`
                      )}
                    >
                      预览
                    </Button>
                  </div>
                </div>
              }
              actions={[
                <Button
                  type="link"
                  icon={<EyeOutlined />}
                  onClick={() => handlePreview(
                    invoice.imageUrl || invoiceService.getCroppedImagePreviewUrl(invoice.filename),
                    `裁切后发票 ${invoice.index + 1}`
                  )}
                >
                  预览
                </Button>,
                <Button
                  type="link"
                  icon={<DownloadOutlined />}
                  onClick={() => handleDownload(invoice.filename, invoice.downloadUrl)}
                >
                  下载
                </Button>,
              ]}
            >
              <Card.Meta
                title={`发票 ${invoice.index + 1}`}
                description={
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    <div>页码: {invoice.page}</div>
                    <div>
                      坐标: [{invoice.bbox.join(', ')}]
                    </div>
                    <Tag color={invoice.confidence > 0.9 ? 'green' : 'orange'}>
                      置信度: {(invoice.confidence * 100).toFixed(1)}%
                    </Tag>
                    {invoice.originalImageUrl && (
                      <Space>
                        <Button
                          type="link"
                          size="small"
                          onClick={() => handlePreview(
                            invoice.originalImageUrl!,
                            `原始图片 - 第 ${invoice.page} 页`
                          )}
                        >
                          查看原始图片
                        </Button>
                      </Space>
                    )}
                  </Space>
                }
              />
            </Card>
          </List.Item>
        )}
      />
      
      <Modal
        open={previewVisible}
        title={previewTitle}
        footer={null}
        onCancel={() => setPreviewVisible(false)}
        width={800}
        centered
      >
        <Image
          src={previewImage}
          alt={previewTitle}
          style={{ width: '100%' }}
          preview={{
            mask: '点击放大',
          }}
        />
      </Modal>
    </>
  );
};

export default InvoiceList;

