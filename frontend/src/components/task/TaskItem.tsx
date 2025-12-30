import React, { useState, useEffect, useCallback } from 'react';
import { Card, Tag, Button, Space, Descriptions, Empty, Spin, Progress, Tabs, Image, Modal, List, message } from 'antd';
import { EyeOutlined, DownloadOutlined, DeleteOutlined, ReloadOutlined, FileImageOutlined } from '@ant-design/icons';
import invoiceService from '../../services/invoiceService';
import InvoiceList from '../result/InvoiceList';
import type { TaskStatusResponse, InvoiceInfo } from '../../types/api';
import { deleteTask as removeTask, updateTaskStatus, getAllTasks } from '../../utils/taskStorage';

interface TaskItemProps {
  taskId: string;
  onDelete?: (taskId: string) => void;
}

const TaskItem: React.FC<TaskItemProps> = ({ taskId, onDelete }) => {
  const [taskStatus, setTaskStatus] = useState<TaskStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewImage, setPreviewImage] = useState<string>('');
  const [previewTitle, setPreviewTitle] = useState<string>('');
  const [activeTab, setActiveTab] = useState<string>('original');
  const [imageErrors, setImageErrors] = useState<Set<string>>(new Set());

  const fetchTaskStatus = useCallback(async () => {
    setLoading(true);
    try {
      const response = await invoiceService.getTaskStatus(taskId);
      const taskData = response.data;
      setTaskStatus(taskData);
      
      // 更新localStorage中的任务状态
      updateTaskStatus(taskId, taskData.status, {
        totalInvoices: taskData.totalInvoices,
        completedAt: taskData.completedAt,
      });
      
      // 如果任务完成，自动展开发票列表
      if (taskData.status === 'COMPLETED' && taskData.invoices && taskData.invoices.length > 0) {
        // setExpanded(true);
      }
    } catch (error: any) {
      console.error('查询任务状态失败，尝试从localStorage读取', error);
      
      // 如果后端查询失败（404），尝试从localStorage读取
      if (error.response?.status === 404 || error.status === 404) {
        const storedTasks = getAllTasks();
        const storedTask = storedTasks.find(t => t.taskId === taskId);
        
        if (storedTask) {
          // 从localStorage恢复任务数据
          setTaskStatus({
            taskId: storedTask.taskId,
            status: storedTask.status,
            progress: storedTask.status === 'COMPLETED' ? 100 : 0,
            totalInvoices: storedTask.totalInvoices,
            invoices: storedTask.invoices || [],
            createdAt: storedTask.createdAt,
            completedAt: storedTask.completedAt,
          } as TaskStatusResponse);
          
          if (storedTask.status === 'COMPLETED' && storedTask.invoices && storedTask.invoices.length > 0) {
            // setExpanded(true);
          }
        } else {
          // 任务不存在
          setTaskStatus({
            taskId,
            status: 'FAILED',
            progress: 0,
            createdAt: new Date().toISOString(),
          } as TaskStatusResponse);
        }
      } else {
        // 其他错误
        setTaskStatus({
          taskId,
          status: 'FAILED',
          progress: 0,
          createdAt: new Date().toISOString(),
        } as TaskStatusResponse);
      }
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    fetchTaskStatus();
  }, [fetchTaskStatus]);

  useEffect(() => {
    // 如果任务正在处理中，每5秒自动刷新一次
    if (taskStatus?.status === 'PROCESSING' || taskStatus?.status === 'PENDING') {
      const interval = setInterval(() => {
        fetchTaskStatus();
      }, 5000);
      
      return () => clearInterval(interval);
    }
  }, [taskStatus?.status, fetchTaskStatus]);

  const handleDelete = () => {
    removeTask(taskId);
    onDelete?.(taskId);
  };

  if (loading) {
    return (
      <Card>
        <Spin tip="加载中..." />
      </Card>
    );
  }

  if (!taskStatus) {
    return (
      <Card>
        <Empty description="任务不存在或已过期" />
      </Card>
    );
  }

  const statusColor = {
    PENDING: 'default',
    PROCESSING: 'processing',
    COMPLETED: 'success',
    FAILED: 'error',
  }[taskStatus.status];

  const statusText = {
    PENDING: '等待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }[taskStatus.status];

  return (
    <Card
      title={
        <Space>
          <span>任务: {taskId.substring(0, 8)}...</span>
          <Tag color={statusColor}>{statusText}</Tag>
        </Space>
      }
      extra={
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchTaskStatus}
            loading={loading}
            size="small"
          >
            刷新
          </Button>
          <Button
            icon={<DeleteOutlined />}
            onClick={handleDelete}
            danger
            size="small"
          >
            删除
          </Button>
        </Space>
      }
      style={{ marginBottom: '16px' }}
    >
      <Descriptions column={2} size="small">
        <Descriptions.Item label="任务ID">{taskStatus.taskId}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={statusColor}>{statusText}</Tag>
        </Descriptions.Item>
        {taskStatus.totalInvoices !== undefined && (
          <Descriptions.Item label="发票数量">
            {taskStatus.totalInvoices} 张
          </Descriptions.Item>
        )}
        <Descriptions.Item label="创建时间">
          {new Date(taskStatus.createdAt).toLocaleString('zh-CN')}
        </Descriptions.Item>
        {taskStatus.completedAt && (
          <Descriptions.Item label="完成时间">
            {new Date(taskStatus.completedAt).toLocaleString('zh-CN')}
          </Descriptions.Item>
        )}
        {taskStatus.status === 'PROCESSING' && (
          <>
            <Descriptions.Item label="进度" span={2}>
              <Progress percent={taskStatus.progress} size="small" status="active" />
              {taskStatus.statusMessage && (
                <div style={{ marginTop: '8px', color: '#666', fontSize: '12px' }}>
                  {taskStatus.statusMessage}
                </div>
              )}
            </Descriptions.Item>
          </>
        )}
      </Descriptions>

      {taskStatus.status === 'COMPLETED' && taskStatus.invoices && taskStatus.invoices.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'original',
                label: (
                  <span>
                    <FileImageOutlined /> 原始文件
                  </span>
                ),
                children: (
                  <div style={{ marginTop: '16px' }}>
                    {(() => {
                      // 获取所有唯一的页码
                      const pages = Array.from(
                        new Set(taskStatus.invoices!.map(inv => inv.page))
                      ).sort((a, b) => a - b);
                      
                      return (
                        <div>
                          {pages.map((page) => {
                            const pageInvoices = taskStatus.invoices!.filter(inv => inv.page === page);
                            const originalImageUrl = invoiceService.getOriginalImagePreviewUrl(taskId, page);
                            
                            return (
                              <Card
                                key={page}
                                title={`第 ${page} 页`}
                                style={{ marginBottom: '16px' }}
                                extra={
                                  <Space>
                                    <Button
                                      icon={<EyeOutlined />}
                                      onClick={() => {
                                        if (!imageErrors.has(`original_${taskId}_${page}`)) {
                                          setPreviewImage(originalImageUrl);
                                          setPreviewTitle(`原始文件 - 第 ${page} 页`);
                                          setPreviewVisible(true);
                                        } else {
                                          message.warning('原始文件不存在或已过期');
                                        }
                                      }}
                                      disabled={imageErrors.has(`original_${taskId}_${page}`)}
                                    >
                                      预览
                                    </Button>
                                    <Button
                                      icon={<DownloadOutlined />}
                                      onClick={() => {
                                        const url = invoiceService.getOriginalImageDownloadUrl(taskId, page);
                                        window.open(url, '_blank');
                                      }}
                                    >
                                      下载
                                    </Button>
                                  </Space>
                                }
                              >
                                <div style={{ textAlign: 'center', marginBottom: '16px' }}>
                                  {imageErrors.has(`original_${taskId}_${page}`) ? (
                                    <div style={{ padding: '40px', textAlign: 'center', color: '#999', border: '1px dashed #d9d9d9', borderRadius: '4px' }}>
                                      <FileImageOutlined style={{ fontSize: '48px', marginBottom: '16px' }} />
                                      <div>原始文件不存在或已过期</div>
                                    </div>
                                  ) : (
                                    <Image
                                      src={originalImageUrl}
                                      alt={`原始文件 - 第 ${page} 页`}
                                      style={{ maxWidth: '100%', maxHeight: '500px', cursor: 'pointer' }}
                                      preview={{
                                        mask: '点击放大',
                                      }}
                                      onError={() => {
                                        setImageErrors(prev => new Set(prev).add(`original_${taskId}_${page}`));
                                      }}
                                    />
                                  )}
                                </div>
                                <div>
                                  <div style={{ marginBottom: '8px' }}>
                                    <Tag color="blue">本页识别到 {pageInvoices.length} 张发票</Tag>
                                  </div>
                                  <List
                                    size="small"
                                    bordered
                                    dataSource={pageInvoices}
                                    renderItem={(invoice: InvoiceInfo, idx: number) => (
                                      <List.Item>
                                        <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
                                          <Space>
                                            <Tag color="red">发票 {idx + 1}</Tag>
                                            <span>坐标: [{invoice.bbox.join(', ')}]</span>
                                            <Tag color={invoice.confidence > 0.9 ? 'green' : 'orange'}>
                                              置信度: {(invoice.confidence * 100).toFixed(1)}%
                                            </Tag>
                                          </Space>
                                          <Button
                                            type="link"
                                            size="small"
                                            icon={<EyeOutlined />}
                                            onClick={() => {
                                              const croppedUrl = invoiceService.getCroppedImagePreviewUrl(invoice.filename);
                                              setPreviewImage(croppedUrl);
                                              setPreviewTitle(`裁切后发票 ${idx + 1} - 第 ${page} 页`);
                                              setPreviewVisible(true);
                                            }}
                                          >
                                            预览裁切
                                          </Button>
                                        </Space>
                                      </List.Item>
                                    )}
                                  />
                                </div>
                              </Card>
                            );
                          })}
                        </div>
                      );
                    })()}
                  </div>
                ),
              },
              {
                key: 'cropped',
                label: (
                  <span>
                    <EyeOutlined /> 裁切后发票 ({taskStatus.totalInvoices})
                  </span>
                ),
                children: (
                  <div style={{ marginTop: '16px' }}>
                    <InvoiceList invoices={taskStatus.invoices} taskId={taskStatus.taskId} />
                  </div>
                ),
              },
            ]}
          />
        </div>
      )}

      {taskStatus.status === 'FAILED' && (
        <div style={{ marginTop: '16px', color: '#ff4d4f' }}>
          任务处理失败，请重新上传文件
        </div>
      )}

      <Modal
        open={previewVisible}
        title={previewTitle}
        footer={null}
        onCancel={() => setPreviewVisible(false)}
        width={900}
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
    </Card>
  );
};

export default TaskItem;

