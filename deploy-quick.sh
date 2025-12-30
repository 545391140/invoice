#!/bin/bash

# ============================================
# Invoice Service 快速部署脚本（简化版）
# ============================================

set -e

# 配置
APP_NAME="invoice-service"
APP_VERSION="1.0.0"
JAR_FILE="target/${APP_NAME}-${APP_VERSION}.jar"
PORT="${PORT:-8080}"

echo "=========================================="
echo "  Invoice Service 快速部署"
echo "=========================================="
echo ""

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请先安装 Java 17+"
    exit 1
fi

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 Maven，请先安装 Maven"
    exit 1
fi

# 检查 ARK_API_KEY
if [ -z "$ARK_API_KEY" ]; then
    echo "警告: ARK_API_KEY 环境变量未设置"
    read -p "请输入 ARK_API_KEY: " api_key
    export ARK_API_KEY="$api_key"
fi

# 构建项目
echo "构建项目..."
mvn clean package -DskipTests

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 构建失败"
    exit 1
fi

# 创建必要的目录
mkdir -p logs uploads outputs temp

# 启动应用
echo ""
echo "启动应用..."
echo "应用将在 http://localhost:$PORT 运行"
echo "按 Ctrl+C 停止服务"
echo ""

java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dfile.encoding=UTF-8 \
    "$JAR_FILE"









