# 发票自动识别与裁切系统

基于火山引擎 Doubao-Seed-1.6-vision 模型的发票自动识别与裁切系统，包含完整的前后端实现。

## 项目结构

```
invoice/
├── frontend/              # 前端项目（React + TypeScript）
├── src/                   # 后端项目（Spring Boot）
├── uploads/               # 上传文件目录
├── outputs/               # 输出文件目录
├── temp/                  # 临时文件目录
└── README.md              # 本文件
```

## 功能特性

- ✅ 支持 PDF 和图片文件上传（JPG、PNG等）
- ✅ 自动识别图片中的多张发票
- ✅ 根据识别结果自动裁切发票
- ✅ 提供原始图片和裁切后图片的预览功能
- ✅ 支持同步和异步处理模式
- ✅ 自动资源回收机制（定时清理临时文件）
- ✅ 完整的前端界面（React + Ant Design）

## 快速开始

### 后端服务

#### 1. 配置 API Key

**方式一：使用启动脚本（推荐）**

```bash
# Windows
start-with-key.bat

# Linux/Mac
chmod +x start-with-key.sh
./start-with-key.sh
```

**方式二：使用环境变量**

```bash
# Windows
set ARK_API_KEY=your_api_key_here

# Linux/Mac
export ARK_API_KEY=your_api_key_here
```

**方式三：使用本地配置文件**

在 `src/main/resources/application-local.yml` 中配置 API Key：

```yaml
volcengine:
  ark-api-key: your_api_key_here
```

> 注意：该文件已添加到 .gitignore，不会提交到版本控制，请妥善保管。

#### 2. 编译运行

```bash
# 使用启动脚本（推荐，已包含 API Key 配置）
start-with-key.bat        # Windows
./start-with-key.sh       # Linux/Mac

# 或手动编译运行（需要先配置 API Key）
mvn clean package
java -jar target/invoice-service-1.0.0.jar
```

#### 3. 验证服务

访问 http://localhost:8080/api/v1/invoice/health

### 前端应用

#### 1. 安装依赖

```bash
cd frontend
npm install
```

#### 2. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

#### 3. 构建生产版本

```bash
npm run build
```

## API 文档

### 同步识别与裁切

**POST** `/api/v1/invoice/recognize-and-crop`

**请求参数**:
- `file` (MultipartFile, 必填): PDF 或图片文件
- `cropPadding` (Integer, 可选): 裁切边距，默认 10 像素
- `outputFormat` (String, 可选): 输出格式，默认 "jpg"

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "task-123456",
    "totalInvoices": 3,
    "invoices": [
      {
        "index": 0,
        "page": 1,
        "bbox": [100, 200, 800, 1200],
        "confidence": 0.95,
        "imageUrl": "http://localhost:8080/api/v1/invoice/preview/cropped/invoice_1_0.jpg",
        "downloadUrl": "http://localhost:8080/api/v1/invoice/download/invoice_1_0.jpg",
        "filename": "invoice_1_0.jpg"
      }
    ],
    "processingTime": 2.5
  }
}
```

### 异步识别与裁切

**POST** `/api/v1/invoice/recognize-and-crop/async`

返回任务ID，通过 `/api/v1/invoice/task/{taskId}` 查询结果。

### 预览和下载

- **预览原始图片**: `GET /api/v1/invoice/preview/original/{taskId}?page=1`
- **预览裁切后图片**: `GET /api/v1/invoice/preview/cropped/{filename}`
- **下载裁切后图片**: `GET /api/v1/invoice/download/{filename}`
- **下载原始图片**: `GET /api/v1/invoice/download/original/{taskId}?page=1`

## 技术栈

### 后端
- Java Spring Boot 3.1.0
- 火山引擎 SDK (volcengine-java-sdk-ark-runtime)
- Apache PDFBox 3.0.0
- Spring Task (定时任务)

### 前端
- React 18+
- TypeScript
- Vite
- Ant Design 5
- Axios

## 配置说明

### 后端配置

主要配置在 `src/main/resources/application.yml`:

```yaml
volcengine:
  ark-api-key: ${ARK_API_KEY}
  base-url: https://ark.cn-beijing.volces.com/api/v3
  model:
    name: doubao-seed-1-6-vision-250815

app:
  cleanup:
    enabled: true
    retention-hours: 24
    cron: "0 0 2 * * ?"
```

### 前端配置

环境变量文件：
- `.env.development`: 开发环境 API 地址
- `.env.production`: 生产环境 API 地址

## 部署

### 后端部署

#### Docker 部署

```bash
docker build -t invoice-service .
docker run -p 8080:8080 -e ARK_API_KEY=your_api_key_here invoice-service
```

#### 传统部署

```bash
mvn clean package
java -jar target/invoice-service-1.0.0.jar
```

### 前端部署

#### Nginx 部署

```bash
cd frontend
npm run build
# 将 dist/ 目录部署到 Nginx
```

#### Docker 部署

```bash
cd frontend
docker build -t invoice-frontend .
docker run -p 80:80 invoice-frontend
```

## 注意事项

1. **API Key 配置**: 
   - 使用 `start-with-key.bat` 脚本（已包含配置）
   - 或设置 `ARK_API_KEY` 环境变量
   - 或配置在 `application-local.yml`（不提交到版本控制）
2. **CORS 配置**: 后端已配置 CORS，支持跨域请求
3. **文件大小限制**: 默认最大文件大小为 50MB
4. **资源回收**: 系统自动在每天凌晨2点清理超过24小时的临时文件
5. **异步任务**: 大文件建议使用异步处理模式
6. **安全提示**: 不要将 API Key 提交到版本控制系统

## 开发指南

### 后端开发

```bash
# 启动开发服务器（需要配置 ARK_API_KEY）
mvn spring-boot:run
```

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器会自动代理 `/api` 请求到后端。

## 许可证

MIT License

