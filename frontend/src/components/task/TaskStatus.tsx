import React, { useEffect, useState } from 'react';
import { Card, Progress, Tag, Button, Space } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import invoiceService from '../../services/invoiceService';
import type { TaskStatusResponse } from '../../types/api';

interface TaskStatusProps {
  taskId: string;
  onComplete?: (response: TaskStatusResponse) => void;
}

const TaskStatus: React.FC<TaskStatusProps> = ({ taskId, onComplete }) => {
  const [taskStatus, setTaskStatus] = useState<TaskStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchTaskStatus = async () => {
    setLoading(true);
    try {
      const response = await invoiceService.getTaskStatus(taskId);
      setTaskStatus(response.data);
      
      if (response.data.status === 'COMPLETED' && onComplete) {
        onComplete(response.data);
      }
    } catch (error) {
      console.error('查询任务状态失败', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTaskStatus();
    const interval = setInterval(() => {
      if (taskStatus?.status === 'PROCESSING' || taskStatus?.status === 'PENDING') {
        fetchTaskStatus();
      }
    }, 2000); // 每2秒轮询一次

    return () => clearInterval(interval);
  }, [taskId, taskStatus?.status]);

  if (!taskStatus) {
    return <div>加载中...</div>;
  }

  const statusColor = {
    PENDING: 'default',
    PROCESSING: 'processing',
    COMPLETED: 'success',
    FAILED: 'error',
  }[taskStatus.status];

  return (
    <Card
      title={`任务 ${taskId}`}
      extra={
        <Button
          icon={<ReloadOutlined />}
          onClick={fetchTaskStatus}
          loading={loading}
        >
          刷新
        </Button>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <div>
          状态: <Tag color={statusColor}>{taskStatus.status}</Tag>
        </div>
        {taskStatus.status === 'PROCESSING' && (
          <Progress percent={taskStatus.progress} />
        )}
        {taskStatus.status === 'COMPLETED' && (
          <div>
            <div>识别到 {taskStatus.totalInvoices} 张发票</div>
            <div>完成时间: {new Date(taskStatus.completedAt!).toLocaleString()}</div>
          </div>
        )}
      </Space>
    </Card>
  );
};

export default TaskStatus;







