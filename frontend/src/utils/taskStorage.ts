import type { TaskStatusResponse } from '../types/api';

const STORAGE_KEY = 'invoice_tasks';

export interface StoredTask {
  taskId: string;
  status: TaskStatusResponse['status'];
  totalInvoices?: number;
  createdAt: string;
  completedAt?: string;
  filename?: string; // 原始文件名
  invoices?: any[]; // 发票列表（用于同步任务，直接从响应中保存）
}

/**
 * 获取所有已存储的任务
 */
export const getAllTasks = (): StoredTask[] => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return [];
    return JSON.parse(stored);
  } catch (error) {
    console.error('读取任务列表失败:', error);
    return [];
  }
};

/**
 * 保存任务到localStorage
 */
export const saveTask = (task: StoredTask): void => {
  try {
    const tasks = getAllTasks();
    const existingIndex = tasks.findIndex(t => t.taskId === task.taskId);
    
    if (existingIndex >= 0) {
      // 更新现有任务
      tasks[existingIndex] = task;
    } else {
      // 添加新任务
      tasks.unshift(task); // 新任务添加到最前面
    }
    
    // 只保留最近100个任务
    const limitedTasks = tasks.slice(0, 100);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(limitedTasks));
  } catch (error) {
    console.error('保存任务失败:', error);
  }
};

/**
 * 更新任务状态
 */
export const updateTaskStatus = (
  taskId: string,
  status: TaskStatusResponse['status'],
  data?: Partial<TaskStatusResponse>
): void => {
  try {
    const tasks = getAllTasks();
    const taskIndex = tasks.findIndex(t => t.taskId === taskId);
    
    if (taskIndex >= 0) {
      tasks[taskIndex] = {
        ...tasks[taskIndex],
        status,
        ...(data?.totalInvoices !== undefined && { totalInvoices: data.totalInvoices }),
        ...(data?.completedAt && { completedAt: data.completedAt }),
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
    }
  } catch (error) {
    console.error('更新任务状态失败:', error);
  }
};

/**
 * 删除任务
 */
export const deleteTask = (taskId: string): void => {
  try {
    const tasks = getAllTasks();
    const filtered = tasks.filter(t => t.taskId !== taskId);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));
  } catch (error) {
    console.error('删除任务失败:', error);
  }
};

/**
 * 清空所有任务
 */
export const clearAllTasks = (): void => {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch (error) {
    console.error('清空任务列表失败:', error);
  }
};

