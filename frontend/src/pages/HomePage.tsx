import React, { useState } from 'react';
import { Card, Form, InputNumber, Radio, Divider } from 'antd';
import FileUpload from '../components/upload/FileUpload';
import InvoiceList from '../components/result/InvoiceList';
import type { InvoiceRecognizeResponse } from '../types/api';

const HomePage: React.FC = () => {
  const [form] = Form.useForm();
  const [result, setResult] = useState<InvoiceRecognizeResponse | null>(null);
  const [processingMode, setProcessingMode] = useState<'sync' | 'async'>('sync');

  const handleSuccess = (response: any) => {
    if (response?.data?.invoices) {
      setResult(response.data);
    }
  };

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
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

