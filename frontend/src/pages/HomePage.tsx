import React, { useState } from 'react';
import { Card, Form, InputNumber, Radio, Divider, message } from 'antd';
import FileUpload from '../components/upload/FileUpload';
import InvoiceList from '../components/result/InvoiceList';
import type { InvoiceRecognizeResponse } from '../types/api';
import { saveTask } from '../utils/taskStorage';

const HomePage: React.FC = () => {
  const [form] = Form.useForm();
  const [result, setResult] = useState<InvoiceRecognizeResponse | null>(null);
  const [processingMode, setProcessingMode] = useState<'sync' | 'async'>('sync');

  const handleSuccess = (response: any) => {
    console.log('收到响应数据:', response);
    // 响应拦截器已经返回了 response.data，所以这里 response 就是 ApiResponse
    // ApiResponse 的结构: { code, message, data: InvoiceRecognizeResponse, timestamp }
    
    // 检查响应是否有效
    if (!response || response.code !== 200) {
      console.warn('响应无效:', response);
      return;
    }
    
    const invoiceData = response.data;
    
    // 如果是异步任务提交成功，不需要在这里显示发票结果
    if (invoiceData?.status === 'PROCESSING') {
      console.log('异步任务已提交:', invoiceData.taskId);
      setResult(null); // 清除上一次的结果
      return;
    }

    if (invoiceData?.invoices && invoiceData.invoices.length > 0) {
      console.log('设置结果，发票数量:', invoiceData.invoices.length);
      setResult(invoiceData);
      
      // 保存任务到localStorage（包含完整的发票数据）
      if (invoiceData.taskId) {
        saveTask({
          taskId: invoiceData.taskId,
          status: 'COMPLETED',
          totalInvoices: invoiceData.totalInvoices,
          createdAt: new Date().toISOString(),
          completedAt: new Date().toISOString(),
          invoices: invoiceData.invoices, // 保存发票列表，这样任务列表可以直接显示
        });
        message.success(`成功识别到 ${invoiceData.totalInvoices} 张发票，已保存到任务列表`);
      }
    } else {
      console.warn('响应中没有发票数据:', response);
      setResult({
        taskId: invoiceData?.taskId || 'unknown',
        totalInvoices: 0,
        invoices: [],
        processingTime: invoiceData?.processingTime || 0
      });
      message.warning('未识别到发票内容，可能是因为图片不清晰或格式特殊。建议检查文件后重试。');
    }
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <Card title="发票识别与裁切" style={{ marginBottom: '24px' }}>
        <Form form={form} layout="vertical">
          <Form.Item label="处理模式">
            <Radio.Group
              value={processingMode}
              onChange={(e) => setProcessingMode(e.target.value)}
            >
              <Radio value="sync">同步处理</Radio>
              <Radio value="async">异步处理</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="裁切边距（像素）" name="cropPadding" initialValue={10}>
            <InputNumber min={0} max={50} />
          </Form.Item>

          <Form.Item label="输出格式" name="outputFormat" initialValue="jpg">
            <Radio.Group>
              <Radio value="jpg">JPG</Radio>
              <Radio value="png">PNG</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="上传文件">
            <FileUpload
              async={processingMode === 'async'}
              cropPadding={form.getFieldValue('cropPadding')}
              outputFormat={form.getFieldValue('outputFormat')}
              onSuccess={handleSuccess}
            />
          </Form.Item>
        </Form>
      </Card>

      {result && (
        <>
          <Divider />
          <Card title={`识别结果（共 ${result.totalInvoices} 张发票）`}>
            <InvoiceList invoices={result.invoices} taskId={result.taskId} />
          </Card>
        </>
      )}
    </div>
  );
};

export default HomePage;

