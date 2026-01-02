#!/bin/bash

# ==========================================
# 发票识别与裁切系统 - EC2 一键部署脚本
# ==========================================

# --- 配置区 ---
EC2_IP="54.238.122.205"
EC2_USER="ec2-user"
PEM_PATH="/Users/liuzhijian/Downloads/5453.pem"
REMOTE_DIR="~/invoice-service"
MVN_PATH="/Users/liuzhijian/Downloads/maven-mvnd-1.0.3-darwin-aarch64/mvn/bin/mvn"

echo "🚀 开始部署流程..."

# 1. 构建前端
echo "📦 Step 1: 构建前端项目..."
cd frontend
# 设置生产环境 API 路径为相对路径
echo "VITE_API_BASE_URL=/api/v1/invoice" > .env.production
npm install && npm run build
if [ $? -ne 0 ]; then echo "❌ 前端构建失败"; exit 1; fi
cd ..

# 2. 同步前端产物到后端
echo "🚚 Step 2: 复制前端静态资源到后端..."
mkdir -p src/main/resources/static
cp -r frontend/dist/* src/main/resources/static/

# 3. 后端打包
echo "☕ Step 3: 后端打包 (JAR)..."
$MVN_PATH clean package -DskipTests
if [ $? -ne 0 ]; then echo "❌ 后端打包失败"; exit 1; fi

# 4. 上传到 EC2
echo "☁️ Step 4: 上传 JAR 包和配置到 EC2..."
ssh -i $PEM_PATH $EC2_USER@$EC2_IP "mkdir -p $REMOTE_DIR/config"
scp -i $PEM_PATH target/invoice-service-1.0.0.jar $EC2_USER@$EC2_IP:$REMOTE_DIR/
scp -i $PEM_PATH src/main/resources/application.yml $EC2_USER@$EC2_IP:$REMOTE_DIR/config/
scp -i $PEM_PATH src/main/resources/application-local.yml $EC2_USER@$EC2_IP:$REMOTE_DIR/config/

# 5. 远程重启服务
echo "🔄 Step 5: 远程重启服务..."
ssh -i $PEM_PATH $EC2_USER@$EC2_IP "bash -s" << EOF
    cd $REMOTE_DIR
    # 停止旧进程
    pkill -f invoice-service-1.0.0.jar || true
    # 启动新进程，显式指定端口为 3002 以覆盖 local 配置中的 8080
    nohup java -jar invoice-service-1.0.0.jar --server.port=3002 --spring.config.location=file:./config/application.yml,file:./config/application-local.yml > output.log 2>&1 &
    echo "✅ 服务已在后台启动，端口: 3002"
EOF

echo "✨ 部署完成！"
echo "🌐 访问地址: http://$EC2_IP:3002"

