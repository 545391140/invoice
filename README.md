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

## 环境要求

### 后端环境

- **JDK**: 17 或更高版本
  ```bash
  # 检查 Java 版本
  java -version
  ```
  - 下载地址: [Eclipse Adoptium](https://adoptium.net/) 或 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)

- **Maven**: 3.6+ 或更高版本
  ```bash
  # 检查 Maven 版本
  mvn -version
  ```
  - 下载地址: [Apache Maven](https://maven.apache.org/download.cgi)
  - Maven 会自动下载项目依赖，无需手动安装

- **火山引擎 API Key**: 必需
  - 获取方式: 登录火山引擎控制台，创建推理接入点获取 API Key

### 前端环境

- **Node.js**: 16+ 或更高版本（推荐 18+）
  ```bash
  # 检查 Node.js 版本
  node -v
  ```
  - 下载地址: [Node.js 官网](https://nodejs.org/)

- **npm**: 通常随 Node.js 一起安装
  ```bash
  # 检查 npm 版本
  npm -v
  ```

### 系统要求

- **操作系统**: Windows、Linux、macOS
- **内存**: 建议 4GB 以上
- **磁盘空间**: 至少 500MB（用于依赖和构建产物）

## 快速开始

### 后端服务

#### 1. 安装依赖

Maven 会自动下载所有依赖，首次运行 `mvn clean package` 时会自动下载。

**手动验证依赖**:
```bash
# 查看依赖树
mvn dependency:tree

# 下载依赖（不编译）
mvn dependency:resolve
```

#### 2. 配置 API Key

**方式一：使用环境变量（推荐）**

```bash
# Windows
set ARK_API_KEY=your_api_key_here

# Linux/Mac
export ARK_API_KEY=your_api_key_here
```

**方式二：使用本地配置文件**

在 `src/main/resources/application-local.yml` 中配置 API Key：

```yaml
volcengine:
  ark-api-key: your_api_key_here
```

> 注意：该文件已添加到 .gitignore，不会提交到版本控制，请妥善保管。

#### 3. 编译运行

```bash
# 使用启动脚本（需要先设置 ARK_API_KEY 环境变量）
chmod +x start.sh          # Linux/Mac（首次运行）
./start.sh                 # Linux/Mac
start.bat                  # Windows

# 或手动编译运行（需要先配置 API Key）
mvn clean package
java -jar target/invoice-service-1.0.0.jar
```

**说明**:
- 首次运行 `mvn clean package` 会下载所有依赖，可能需要几分钟
- 如果下载依赖失败，检查网络连接或配置 Maven 镜像源

#### 4. 验证服务

访问 http://localhost:8080/api/v1/invoice/health

### 前端应用

#### 1. 安装依赖

```bash
cd frontend
npm install
```

**说明**:
- 首次运行 `npm install` 会下载所有依赖，可能需要几分钟
- 如果下载失败，可以使用国内镜像源：
  ```bash
  npm config set registry https://registry.npmmirror.com
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
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING"
  }
}
```

返回任务ID后，通过 `/api/v1/invoice/task/{taskId}` 查询结果。

### 查询任务状态

**GET** `/api/v1/invoice/task/{taskId}`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "progress": 100,
    "totalInvoices": 3,
    "invoices": [...],
    "createdAt": "2024-01-01T12:00:00Z",
    "completedAt": "2024-01-01T12:00:30Z"
  }
}
```

### 预览和下载

- **预览原始图片**: `GET /api/v1/invoice/preview/original/{taskId}?page=1`
- **预览裁切后图片**: `GET /api/v1/invoice/preview/cropped/{filename}`
- **下载裁切后图片**: `GET /api/v1/invoice/download/{filename}`
- **下载原始图片**: `GET /api/v1/invoice/download/original/{taskId}?page=1`

### 健康检查

**GET** `/api/v1/invoice/health`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "version": "1.0.0"
  }
}
```

### API 信息

**GET** `/`

返回所有可用的 API 端点信息。

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "service": "Invoice Auto Crop Service",
    "version": "1.0.0",
    "description": "发票自动识别与裁切服务",
    "endpoints": [
      "POST /api/v1/invoice/recognize-and-crop - 同步识别与裁切",
      "POST /api/v1/invoice/recognize-and-crop/async - 异步识别与裁切",
      "GET /api/v1/invoice/task/{taskId} - 查询任务状态",
      "GET /api/v1/invoice/preview/original/{taskId}?page=1 - 预览原始图片",
      "GET /api/v1/invoice/preview/cropped/{filename} - 预览裁切后的图片",
      "GET /api/v1/invoice/download/{filename} - 下载裁切后的图片",
      "GET /api/v1/invoice/download/original/{taskId}?page=1 - 下载原始图片",
      "GET /api/v1/invoice/health - 健康检查"
    ]
  }
}
```

## API 调用示例

### 使用 curl

#### 同步调用
```bash
curl -X POST "http://localhost:8080/api/v1/invoice/recognize-and-crop" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@invoice.pdf" \
  -F "cropPadding=10" \
  -F "outputFormat=jpg"
```

#### 异步调用
```bash
# 1. 提交任务
curl -X POST "http://localhost:8080/api/v1/invoice/recognize-and-crop/async" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@invoice.pdf"

# 2. 查询任务状态（使用返回的 taskId）
curl "http://localhost:8080/api/v1/invoice/task/{taskId}"
```

### 使用 Python

```python
import requests

# 同步调用
url = "http://localhost:8080/api/v1/invoice/recognize-and-crop"
files = {'file': open('invoice.pdf', 'rb')}
data = {'cropPadding': 10, 'outputFormat': 'jpg'}

response = requests.post(url, files=files, data=data)
result = response.json()
print(result)
```

### 使用 JavaScript/Node.js

```javascript
const FormData = require('form-data');
const fs = require('fs');
const axios = require('axios');

const formData = new FormData();
formData.append('file', fs.createReadStream('invoice.pdf'));
formData.append('cropPadding', '10');
formData.append('outputFormat', 'jpg');

axios.post('http://localhost:8080/api/v1/invoice/recognize-and-crop', formData, {
  headers: formData.getHeaders()
})
.then(response => console.log(response.data))
.catch(error => console.error(error));
```

## 其他应用调用服务

### 服务配置

1. **启动服务**
   ```bash
   export ARK_API_KEY=your_api_key_here
   ./start.sh
   ```

2. **网络访问**
   - 本地访问: `http://localhost:8080`
   - 局域网访问: `http://your-server-ip:8080`
   - 公网访问: 配置反向代理或端口转发

3. **CORS 配置**
   - 已配置支持跨域请求
   - 允许所有来源、所有HTTP方法
   - 无需额外配置即可被其他应用调用

### 调用要求

- **文件大小限制**: 最大 50MB
- **支持格式**: PDF, JPG, PNG, BMP, GIF
- **超时设置**: API 调用超时 60 秒
- **异步任务**: 大文件建议使用异步处理模式

## 技术栈

### 后端
- **Java**: JDK 17+
- **构建工具**: Maven 3.6+
- **框架**: Spring Boot 3.1.0
- **核心依赖**:
  - 火山引擎 SDK (volcengine-java-sdk-ark-runtime)
  - Apache PDFBox 3.0.0 (PDF处理)
  - OkHttp 4.11.0 (HTTP客户端)
  - Jackson (JSON处理)
  - Commons IO 2.11.0 (文件操作)
  - Lombok (代码简化)
- **其他**: Spring Task (定时任务)

### 前端
- **运行时**: Node.js 16+ (推荐 18+)
- **包管理**: npm
- **框架**: React 18+
- **语言**: TypeScript
- **构建工具**: Vite 5.0+
- **UI组件库**: Ant Design 5
- **HTTP客户端**: Axios
- **状态管理**: Zustand

## 配置说明

### 后端配置

主要配置在 `src/main/resources/application.yml`:

```yaml
volcengine:
  ark-api-key: ${ARK_API_KEY}
  base-url: https://uniapi.ruijie.com.cn/v1
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

## 常见问题

### 依赖安装问题

**Q: Maven 下载依赖很慢或失败？**

A: 配置国内镜像源，编辑 `~/.m2/settings.xml`（如不存在则创建）：
```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

**Q: npm install 失败或很慢？**

A: 使用国内镜像源：
```bash
npm config set registry https://registry.npmmirror.com
npm install
```

**Q: 找不到 java 或 mvn 命令？**

A: 确保已安装 JDK 17 和 Maven，并配置了环境变量 PATH。

### 运行问题

**Q: 启动时提示 "ARK_API_KEY 未配置"？**

A: 确保已设置环境变量：
```bash
export ARK_API_KEY=your_api_key_here  # Linux/Mac
set ARK_API_KEY=your_api_key_here     # Windows
```

**Q: 端口 8080 已被占用？**

A: 修改 `src/main/resources/application.yml` 中的端口：
```yaml
server:
  port: 8081  # 改为其他端口
```

## 注意事项

1. **API Key 配置**: 
   - 必须设置 `ARK_API_KEY` 环境变量
   - 或配置在 `application-local.yml`（不提交到版本控制）
   - 启动脚本会检查环境变量是否设置
2. **CORS 配置**: 后端已配置 CORS，支持跨域请求，其他应用可直接调用
3. **文件大小限制**: 默认最大文件大小为 50MB
4. **资源回收**: 系统自动在每天凌晨2点清理超过24小时的临时文件
5. **异步任务**: 大文件建议使用异步处理模式
6. **安全提示**: 不要将 API Key 提交到版本控制系统
7. **生产环境**: 建议配置 HTTPS、添加认证、配置限流和监控
8. **依赖管理**: Maven 和 npm 会自动管理依赖，无需手动下载

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

