# 发票识别与裁切前端

基于 React + TypeScript + Ant Design 的前端应用，与后端服务完全分离。

## 技术栈

- **框架**: React 18+
- **语言**: TypeScript
- **构建工具**: Vite
- **UI 组件库**: Ant Design
- **HTTP 客户端**: Axios
- **路由**: React Router

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 配置环境变量

创建 `.env.development` 文件（已包含）：

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1/invoice
```

### 3. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 4. 构建生产版本

```bash
npm run build
```

构建产物在 `dist/` 目录。

## 项目结构

```
frontend/
├── src/
│   ├── components/          # 组件
│   │   ├── upload/          # 上传组件
│   │   ├── result/          # 结果展示组件
│   │   └── task/            # 任务管理组件
│   ├── pages/               # 页面
│   │   └── HomePage.tsx     # 首页
│   ├── services/            # API 服务
│   │   ├── api.ts          # API 配置
│   │   └── invoiceService.ts # 发票服务
│   ├── types/              # TypeScript 类型定义
│   │   └── api.ts
│   ├── App.tsx             # 根组件
│   └── main.tsx            # 入口文件
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 功能特性

- ✅ 文件上传（支持 PDF、JPG、PNG）
- ✅ 同步/异步处理模式
- ✅ 识别结果展示
- ✅ 图片预览（原始和裁切后）
- ✅ 图片对比功能
- ✅ 批量下载
- ✅ 任务状态查询（异步模式）

## API 配置

前端通过环境变量配置后端 API 地址：

- **开发环境**: `.env.development`
- **生产环境**: `.env.production`

默认配置：
- 开发环境: `http://localhost:8080/api/v1/invoice`
- 生产环境: `https://api.example.com/api/v1/invoice`

## 部署

### Nginx 部署

```nginx
server {
    listen 80;
    server_name invoice-frontend.example.com;
    root /var/www/invoice-frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://invoice-service:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Docker 部署

```dockerfile
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

## 注意事项

1. 确保后端服务已启动并配置了 CORS
2. 生产环境需要配置正确的 API 地址
3. 前端和后端完全分离，可以独立部署

