# API 测试文档

## 异步识别接口测试

### 接口地址
```
POST http://localhost:8080/api/v1/invoice/recognize-and-crop/async
```

### 请求示例

#### 使用 curl

```bash
curl -X POST "http://localhost:8080/api/v1/invoice/recognize-and-crop/async" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@invoice.pdf" \
  -F "cropPadding=10" \
  -F "outputFormat=jpg"
```

#### 使用 Postman

1. 选择 POST 方法
2. URL: `http://localhost:8080/api/v1/invoice/recognize-and-crop/async`
3. Body 选择 `form-data`
4. 添加字段：
   - `file`: 选择文件（PDF 或图片）
   - `cropPadding`: 10（可选）
   - `outputFormat`: jpg（可选）

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "estimatedTime": null
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### 查询任务状态

使用返回的 `taskId` 查询任务状态：

```
GET http://localhost:8080/api/v1/invoice/task/{taskId}
```

#### 响应示例

**处理中**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "progress": 50,
    "createdAt": "2024-01-01T12:00:00Z"
  }
}
```

**已完成**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "progress": 100,
    "totalInvoices": 3,
    "invoices": [
      {
        "index": 0,
        "page": 1,
        "bbox": [100, 200, 800, 1200],
        "confidence": 0.95,
        "filename": "invoice_1_0.jpg"
      }
    ],
    "createdAt": "2024-01-01T12:00:00Z",
    "completedAt": "2024-01-01T12:00:30Z"
  }
}
```

### 测试步骤

1. **启动后端服务**
   ```bash
   start-with-key.bat  # Windows
   # 或
   ./start-with-key.sh  # Linux/Mac
   ```

2. **发送异步请求**
   使用 Postman 或 curl 发送 POST 请求到 `/recognize-and-crop/async`

3. **获取任务ID**
   从响应中获取 `taskId`

4. **轮询查询状态**
   每隔2-3秒查询一次任务状态，直到状态变为 `COMPLETED` 或 `FAILED`

5. **获取结果**
   当状态为 `COMPLETED` 时，可以从响应中获取识别结果和发票列表

### 注意事项

- 异步任务会将文件保存到临时目录，处理完成后自动清理
- 任务状态存储在内存中，服务重启后会丢失（生产环境建议使用 Redis 或数据库）
- 大文件建议使用异步接口，避免请求超时

















