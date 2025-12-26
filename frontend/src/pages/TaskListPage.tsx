import React, { useState, useEffect } from 'react';
import { Card, Empty, Button, Space, Tag, message, Popconfirm } from 'antd';
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import TaskItem from '../components/task/TaskItem';
import { getAllTasks, clearAllTasks, StoredTask } from '../utils/taskStorage';

const TaskListPage: React.FC = () => {
  const [tasks, setTasks] = useState<StoredTask[]>([]);
  const [loading, setLoading] = useState(false);

  const loadTasks = () => {
    const storedTasks = getAllTasks();
    // 按完成时间倒序排列
    const sortedTasks = storedTasks.sort((a, b) => {
      const timeA = a.completedAt || a.createdAt;
      const timeB = b.completedAt || b.createdAt;
      return new Date(timeB).getTime() - new Date(timeA).getTime();
    });
    setTasks(sortedTasks);
  };

  useEffect(() => {
    loadTasks();
  }, []);

  const handleDelete = (taskId: string) => {
    setTasks(tasks.filter(t => t.taskId !== taskId));
    message.success('任务已删除');
  };

  const handleClearAll = () => {
    clearAllTasks();
    setTasks([]);
    message.success('已清空所有任务');
  };

  const handleRefresh = () => {
    loadTasks();
    message.success('已刷新任务列表');
  };

  const completedCount = tasks.filter(t => t.status === 'COMPLETED').length;
  const processingCount = tasks.filter(t => t.status === 'PROCESSING' || t.status === 'PENDING').length;
  const failedCount = tasks.filter(t => t.status === 'FAILED').length;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <Card
        title="任务列表"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
              刷新
            </Button>
            {tasks.length > 0 && (
              <Popconfirm
                title="确定要清空所有任务吗？"
                onConfirm={handleClearAll}
                okText="确定"
                cancelText="取消"
              >
                <Button icon={<DeleteOutlined />} danger>
                  清空全部
                </Button>
              </Popconfirm>
            )}
          </Space>
        }
        style={{ marginBottom: '24px' }}
      >
        <Space style={{ marginBottom: '16px' }}>
          <Tag color="success">已完成: {completedCount}</Tag>
          <Tag color="processing">处理中: {processingCount}</Tag>
          <Tag color="error">失败: {failedCount}</Tag>
          <Tag>总计: {tasks.length}</Tag>
        </Space>
      </Card>

      {tasks.length === 0 ? (
        <Card>
          <Empty
            description="暂无任务记录"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </Card>
      ) : (
        <div>
          {tasks.map((task) => (
            <TaskItem
              key={task.taskId}
              taskId={task.taskId}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default TaskListPage;

