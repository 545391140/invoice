#!/bin/bash

# ============================================
# Invoice Service 运行时部署脚本
# 版本: 1.0.0
# 功能：在服务器上直接运行已构建好的前后端
# 
# 前提条件：
# - 前端已构建：frontend/dist 目录存在
# - 后端已构建：target/invoice-service-1.0.0.jar 存在
# 
# 使用方法：
#   sudo ./run.sh
# 
# 环境变量：
#   PORT=8080                    # 后端服务端口
#   FRONTEND_PORT=80             # 前端服务端口
#   SERVER_ADDRESS=0.0.0.0       # 监听地址
#   ARK_API_KEY=your_key         # 火山引擎 API 密钥
#   DEPLOY_FRONTEND=true         # 是否部署前端
#   AUTO_START=true              # 是否自动启动服务
# ============================================

# 不使用 set -e，允许某些步骤失败后继续执行
# 这样可以确保即使后端启动失败，前端也能正常部署
set +e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ============================================
# 配置变量（可通过环境变量覆盖）
# ============================================
APP_NAME="invoice-service"
APP_VERSION="1.0.0"
APP_USER="${APP_USER:-invoice}"
APP_DIR="${APP_DIR:-/opt/invoice-service}"
SERVICE_NAME="${APP_NAME}"
JAR_FILE="target/${APP_NAME}-${APP_VERSION}.jar"
JAVA_VERSION="17"
PORT="${PORT:-8080}"
SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"
AUTO_START="${AUTO_START:-true}"
ARK_API_KEY="${ARK_API_KEY:-sk-awaO6Dt5bDW2OTiRJotWuZvtkaIwnYIwAzi0Bwx49MzlZgJz}"
DEPLOY_FRONTEND="${DEPLOY_FRONTEND:-true}"
FRONTEND_PORT="${FRONTEND_PORT:-80}"

# ============================================
# 日志函数
# ============================================
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# ============================================
# 工具函数
# ============================================

# 检查是否为 root 用户
check_root() {
    if [ "$EUID" -ne 0 ]; then 
        log_error "此脚本需要 root 权限运行"
        log_info ""
        log_info "请使用以下方式之一运行："
        log_info "  1. sudo ./run.sh"
        log_info "  2. su - 然后运行 ./run.sh"
        exit 1
    else
        log_success "已检测到 root 权限"
    fi
}

# 检查系统类型
check_system() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
        log_info "检测到系统: $OS $OS_VERSION"
    else
        log_error "无法检测系统类型"
        exit 1
    fi
}

# 检查构建产物
check_build_artifacts() {
    log_step "检查构建产物..."
    
    local has_error=false
    
    # 检查后端 JAR 文件
    if [ ! -f "$JAR_FILE" ]; then
        log_error "未找到后端构建产物: $JAR_FILE"
        log_info "请先在本地构建后端: mvn clean package"
        has_error=true
    else
        log_success "后端构建产物存在: $JAR_FILE"
    fi
    
    # 检查前端构建产物
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        if [ ! -d "frontend/dist" ] || [ -z "$(ls -A frontend/dist 2>/dev/null)" ]; then
            log_error "未找到前端构建产物: frontend/dist"
            log_info "请先在本地构建前端: cd frontend && npm run build"
            has_error=true
        elif [ ! -f "frontend/dist/index.html" ]; then
            log_error "前端构建产物缺少 index.html 文件"
            has_error=true
        else
            log_success "前端构建产物存在: frontend/dist"
        fi
    fi
    
    # 如果后端缺失，但前端存在，仍然允许继续（只部署前端）
    if [ "$has_error" = true ] && [ ! -f "$JAR_FILE" ] && [ "$DEPLOY_FRONTEND" = "true" ]; then
        log_warning "后端构建产物缺失，但将继续部署前端"
        return 0
    fi
    
    # 如果后端缺失且不部署前端，则退出
    if [ "$has_error" = true ] && [ ! -f "$JAR_FILE" ] && [ "$DEPLOY_FRONTEND" != "true" ]; then
        log_error "后端构建产物缺失且未启用前端部署，无法继续"
        exit 1
    fi
    
    return 0
}

# 检测端口是否被占用
check_port() {
    local port=$1
    if command -v netstat &> /dev/null; then
        if netstat -tuln | grep -q ":$port "; then
            return 1  # 端口被占用
        fi
    elif command -v ss &> /dev/null; then
        if ss -tuln | grep -q ":$port "; then
            return 1  # 端口被占用
        fi
    fi
    return 0  # 端口可用
}

# 获取本机 IP 地址
get_local_ip() {
    local ip=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' | head -1)
    if [ -z "$ip" ]; then
        ip=$(hostname -I | awk '{print $1}')
    fi
    if [ -z "$ip" ]; then
        ip=$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1' | head -1)
    fi
    echo "$ip"
}

# ============================================
# 依赖安装函数
# ============================================

# 更新包管理器
update_package_manager() {
    log_info "更新包管理器..."
    case $OS in
        ubuntu|debian)
            export DEBIAN_FRONTEND=noninteractive
            apt-get update -qq
            ;;
        centos|rhel)
            yum makecache -q || true
            ;;
        fedora)
            dnf makecache -q || true
            ;;
    esac
}

# 安装 Java 17
install_java() {
    log_step "检查并安装 Java $JAVA_VERSION..."
    
    if command -v java &> /dev/null; then
        JAVA_VER=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge "$JAVA_VERSION" ] 2>/dev/null; then
            log_success "Java $JAVA_VER 已安装，版本满足要求"
            return 0
        else
            log_warning "Java 版本过低 ($JAVA_VER)，需要 Java $JAVA_VERSION+，将重新安装"
        fi
    else
        log_info "未检测到 Java，开始安装..."
    fi
    
    case $OS in
        ubuntu|debian)
            log_info "安装 OpenJDK $JAVA_VERSION..."
            apt-get install -y -qq openjdk-${JAVA_VERSION}-jdk > /dev/null 2>&1
            ;;
        centos|rhel)
            log_info "安装 OpenJDK $JAVA_VERSION..."
            yum install -y -q java-${JAVA_VERSION}-openjdk java-${JAVA_VERSION}-openjdk-devel > /dev/null 2>&1
            ;;
        fedora)
            log_info "安装 OpenJDK $JAVA_VERSION..."
            dnf install -y -q java-${JAVA_VERSION}-openjdk java-${JAVA_VERSION}-openjdk-devel > /dev/null 2>&1
            ;;
        *)
            log_error "不支持的系统类型: $OS"
            log_info "请手动安装 Java $JAVA_VERSION"
            exit 1
            ;;
    esac
    
    # 验证安装
    if command -v java &> /dev/null; then
        JAVA_VER=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        log_success "Java $JAVA_VER 安装完成"
        java -version 2>&1 | head -n 1
    else
        log_error "Java 安装失败"
        exit 1
    fi
}

# 安装和配置 Nginx
install_nginx() {
    if [ "$DEPLOY_FRONTEND" != "true" ]; then
        return 0
    fi
    
    log_step "检查并安装 Nginx..."
    
    if command -v nginx &> /dev/null; then
        log_success "Nginx 已安装"
        return 0
    fi
    
    case $OS in
        ubuntu|debian)
            apt-get install -y -qq nginx > /dev/null 2>&1
            ;;
        centos|rhel|fedora)
            if [ "$OS" = "fedora" ]; then
                dnf install -y -q nginx > /dev/null 2>&1
            else
                yum install -y -q nginx > /dev/null 2>&1
            fi
            ;;
        *)
            log_error "不支持的系统类型: $OS"
            return 1
            ;;
    esac
    
    if command -v nginx &> /dev/null; then
        log_success "Nginx 安装完成"
    else
        log_error "Nginx 安装失败"
        return 1
    fi
}

# ============================================
# 应用部署函数
# ============================================

# 创建应用用户
create_user() {
    log_step "创建应用用户: $APP_USER"
    
    if id "$APP_USER" &>/dev/null; then
        log_warning "用户 $APP_USER 已存在，跳过创建"
    else
        useradd -r -s /bin/bash -d "$APP_DIR" -m "$APP_USER" 2>/dev/null || {
            log_warning "创建用户失败，可能已存在"
        }
        log_success "用户 $APP_USER 准备就绪"
    fi
}

# 创建应用目录
create_directories() {
    log_step "创建应用目录结构..."
    
    mkdir -p "$APP_DIR"/{bin,logs,uploads,outputs,temp,config}
    chown -R "$APP_USER:$APP_USER" "$APP_DIR"
    
    log_success "目录创建完成: $APP_DIR"
}

# 配置访问地址
configure_server_address() {
    log_step "配置服务器访问地址..."
    
    LOCAL_IP=$(get_local_ip)
    
    if [ "$SERVER_ADDRESS" = "0.0.0.0" ] || [ -z "$SERVER_ADDRESS" ]; then
        if [ -n "$LOCAL_IP" ]; then
            log_info "检测到本机 IP: $LOCAL_IP"
            log_info "服务将监听所有网络接口 (0.0.0.0)，可通过以下地址访问："
            log_info "  - http://localhost:$PORT"
            log_info "  - http://$LOCAL_IP:$PORT"
        else
            log_warning "无法检测本机 IP，服务将监听 0.0.0.0:$PORT"
        fi
    else
        log_info "使用指定的服务器地址: $SERVER_ADDRESS:$PORT"
    fi
    
    # 检查端口是否被占用
    if ! check_port "$PORT"; then
        log_error "端口 $PORT 已被占用，请修改 PORT 环境变量或停止占用该端口的服务"
        exit 1
    fi
    
    log_success "访问地址配置完成"
}

# 创建生产环境配置文件
create_prod_config() {
    log_step "创建生产环境配置文件..."
    
    PROD_CONFIG="$APP_DIR/config/application-prod.yml"
    
    cat > "$PROD_CONFIG" << EOF
# 生产环境配置
# 此文件由部署脚本自动生成

server:
  port: ${PORT}
  address: ${SERVER_ADDRESS}
  servlet:
    context-path: /

spring:
  application:
    name: ${APP_NAME}
  profiles:
    active: prod

# 火山引擎配置
volcengine:
  ark-api-key: ${ARK_API_KEY}
  base-url: https://uniapi.ruijie.com.cn/v1
  model:
    name: doubao-seed-1-6-vision-250815

# 应用配置
app:
  upload-folder: ${APP_DIR}/uploads
  output-folder: ${APP_DIR}/outputs
  temp-folder: ${APP_DIR}/temp
  max-file-size: 52428800
  allowed-extensions:
    pdf: pdf
    image: jpg,jpeg,png,bmp,gif
  cleanup:
    enabled: true
    retention-hours: 24
    cron: "0 0 2 * * ?"

# 图像处理配置
image:
  pdf:
    dpi: 300
    scale: 2.0
  crop:
    padding: 10
    min-size: 100
  output:
    format: jpg
    quality: 0.95

# API 调用配置
api:
  timeout: 60
  retry:
    max-retries: 3
    delay: 1000
  prompt: |
    请识别图片中所有发票的位置。
    对于每张发票，请返回以下信息：
    1. 边界框坐标 (bbox): [x1, y1, x2, y2]，单位为像素
    2. 置信度 (confidence): 0-1之间的数值
    3. 页码 (page): 图片所在的页码
    
    请以 JSON 格式返回结果。

# 日志配置
logging:
  level:
    root: INFO
    com.invoice: INFO
  file:
    name: ${APP_DIR}/logs/invoice-service.log
    max-size: 100MB
    max-history: 30
EOF

    chown "$APP_USER:$APP_USER" "$PROD_CONFIG"
    chmod 600 "$PROD_CONFIG"
    
    log_success "配置文件创建完成: $PROD_CONFIG"
}

# 部署文件
deploy_files() {
    log_step "部署应用文件..."
    
    # 复制 JAR 文件
    cp "$JAR_FILE" "$APP_DIR/bin/"
    chown "$APP_USER:$APP_USER" "$APP_DIR/bin/${APP_NAME}-${APP_VERSION}.jar"
    log_info "JAR 文件已复制到: $APP_DIR/bin/${APP_NAME}-${APP_VERSION}.jar"
    
    # 创建启动脚本
    cat > "$APP_DIR/bin/start.sh" << 'START_EOF'
#!/bin/bash
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_FILE="$APP_DIR/bin/invoice-service-1.0.0.jar"
LOG_FILE="$APP_DIR/logs/invoice-service.log"
PID_FILE="$APP_DIR/bin/app.pid"

cd "$APP_DIR"

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "应用已在运行 (PID: $PID)"
        exit 1
    fi
fi

# 加载环境变量
if [ -f "$APP_DIR/config/env.conf" ]; then
    source "$APP_DIR/config/env.conf"
fi

# 启动应用
nohup java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dspring.profiles.active=prod \
    -Dspring.config.additional-location=file:$APP_DIR/config/application-prod.yml \
    -Dfile.encoding=UTF-8 \
    "$JAR_FILE" > "$LOG_FILE" 2>&1 &

echo $! > "$PID_FILE"
echo "应用已启动 (PID: $(cat $PID_FILE))"
START_EOF

    chmod +x "$APP_DIR/bin/start.sh"
    chown "$APP_USER:$APP_USER" "$APP_DIR/bin/start.sh"
    
    # 创建停止脚本
    cat > "$APP_DIR/bin/stop.sh" << 'STOP_EOF'
#!/bin/bash
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$APP_DIR/bin/app.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "应用未运行"
    exit 1
fi

PID=$(cat "$PID_FILE")
if ps -p "$PID" > /dev/null 2>&1; then
    kill "$PID"
    rm -f "$PID_FILE"
    echo "应用已停止"
else
    echo "应用未运行"
    rm -f "$PID_FILE"
fi
STOP_EOF

    chmod +x "$APP_DIR/bin/stop.sh"
    chown "$APP_USER:$APP_USER" "$APP_DIR/bin/stop.sh"
    
    # 创建环境配置文件
    cat > "$APP_DIR/config/env.conf" << EOF
# 应用环境配置
ARK_API_KEY=${ARK_API_KEY:-}
APP_DIR=$APP_DIR
PORT=$PORT
SERVER_ADDRESS=$SERVER_ADDRESS
EOF

    chown "$APP_USER:$APP_USER" "$APP_DIR/config/env.conf"
    chmod 600 "$APP_DIR/config/env.conf"
    
    log_success "文件部署完成"
}

# 部署前端
deploy_frontend() {
    if [ "$DEPLOY_FRONTEND" != "true" ]; then
        log_info "跳过前端部署（DEPLOY_FRONTEND=false）"
        return 0
    fi
    
    log_step "部署前端应用..."
    
    # 检查前端构建产物是否存在
    if [ ! -d "frontend/dist" ] || [ -z "$(ls -A frontend/dist 2>/dev/null)" ]; then
        log_error "前端构建产物不存在或为空: frontend/dist"
        log_info "请确保前端已构建: cd frontend && npm run build"
        return 1
    fi
    
    if [ ! -f "frontend/dist/index.html" ]; then
        log_error "前端构建产物缺少 index.html 文件"
        return 1
    fi
    
    log_info "前端构建产物检查通过: frontend/dist"
    
    # 检查前端端口是否被占用
    if ! check_port "$FRONTEND_PORT"; then
        log_error "前端端口 $FRONTEND_PORT 已被占用"
        log_info "请修改 FRONTEND_PORT 环境变量或停止占用该端口的服务"
        log_info "检查端口占用: netstat -tuln | grep :$FRONTEND_PORT 或 ss -tuln | grep :$FRONTEND_PORT"
        return 1
    fi
    
    # 创建前端部署目录
    FRONTEND_DIR="/opt/invoice-frontend"
    
    # 清空旧文件（如果存在）
    if [ -d "$FRONTEND_DIR" ]; then
        log_info "清空旧的前端文件..."
        rm -rf "$FRONTEND_DIR"/*
    else
        mkdir -p "$FRONTEND_DIR"
    fi
    
    # 复制构建产物
    log_info "复制前端文件到 $FRONTEND_DIR..."
    if cp -r frontend/dist/* "$FRONTEND_DIR/" 2>/dev/null; then
        log_success "前端文件复制完成"
        
        # 统计复制的文件数量
        local file_count=$(find "$FRONTEND_DIR" -type f | wc -l)
        log_info "已复制 $file_count 个文件"
        
        # 验证关键文件是否存在
        if [ ! -f "$FRONTEND_DIR/index.html" ]; then
            log_error "复制后缺少 index.html 文件"
            return 1
        fi
        
        # 设置正确的权限
        if id nginx &>/dev/null; then
            chown -R nginx:nginx "$FRONTEND_DIR" 2>/dev/null
            log_info "已设置文件所有者为 nginx:nginx"
        elif id www-data &>/dev/null; then
            chown -R www-data:www-data "$FRONTEND_DIR" 2>/dev/null
            log_info "已设置文件所有者为 www-data:www-data"
        else
            log_warning "未找到 nginx 或 www-data 用户，跳过权限设置"
        fi
        chmod -R 755 "$FRONTEND_DIR"
        log_info "已设置文件权限为 755"
    else
        log_error "前端文件复制失败"
        log_info "请检查 frontend/dist 目录是否存在且可读"
        return 1
    fi
    
    # 创建 Nginx 配置
    NGINX_CONF="/etc/nginx/conf.d/invoice-frontend.conf"
    log_info "创建 Nginx 配置文件: $NGINX_CONF"
    
    cat > "$NGINX_CONF" << EOF
server {
    listen ${FRONTEND_PORT};
    server_name _;
    root $FRONTEND_DIR;
    index index.html;

    # 前端路由支持（SPA）
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # API 代理
    location /api {
        proxy_pass http://localhost:${PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # 增加超时时间（处理大文件）
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 禁用访问日志（可选）
    access_log off;
    error_log /var/log/nginx/invoice-frontend-error.log;
}
EOF

    log_info "Nginx 配置文件已创建"
    
    # 测试 Nginx 配置
    log_info "验证 Nginx 配置..."
    local nginx_test_output
    nginx_test_output=$(nginx -t 2>&1)
    local nginx_test_result=$?
    
    if [ $nginx_test_result -eq 0 ]; then
        log_success "Nginx 配置验证通过"
    else
        log_error "Nginx 配置验证失败"
        echo "$nginx_test_output"
        log_info "请检查配置文件: $NGINX_CONF"
        return 1
    fi
    
    # 停止现有 Nginx（如果正在运行）
    if systemctl is-active --quiet nginx 2>/dev/null; then
        log_info "停止现有 Nginx 服务..."
        systemctl stop nginx > /dev/null 2>&1
        sleep 2
    fi
    
    # 启动并启用 Nginx
    log_info "启动 Nginx 服务..."
    systemctl enable nginx > /dev/null 2>&1
    
    if ! systemctl start nginx 2>&1; then
        log_error "Nginx 启动失败"
        log_info "Nginx 状态:"
        systemctl status nginx --no-pager -l | head -n 20
        log_info "Nginx 错误日志:"
        tail -n 20 /var/log/nginx/error.log 2>/dev/null || echo "无法读取错误日志"
        return 1
    fi
    
    # 等待 Nginx 启动
    sleep 3
    
    # 检查 Nginx 状态
    if systemctl is-active --quiet nginx; then
        log_success "Nginx 启动成功"
        
        # 检查端口是否真的在监听
        if ! check_port "$FRONTEND_PORT"; then
            log_success "端口 $FRONTEND_PORT 正在监听"
        else
            log_warning "端口 $FRONTEND_PORT 未在监听，Nginx 可能未正确启动"
            log_info "检查 Nginx 进程: ps aux | grep nginx"
            log_info "检查端口监听: netstat -tuln | grep :$FRONTEND_PORT"
            return 1
        fi
        
        LOCAL_IP=$(get_local_ip)
        if [ -n "$LOCAL_IP" ]; then
            log_info "前端访问地址: http://$LOCAL_IP:${FRONTEND_PORT}"
        fi
        log_info "前端本地访问: http://localhost:${FRONTEND_PORT}"
    else
        log_error "Nginx 启动后未运行"
        log_info "Nginx 状态:"
        systemctl status nginx --no-pager -l | head -n 20
        log_info "Nginx 错误日志:"
        tail -n 30 /var/log/nginx/error.log 2>/dev/null || echo "无法读取错误日志"
        log_info "检查 Nginx 配置: nginx -t"
        return 1
    fi
    
    return 0
}

# 创建 systemd 服务
create_systemd_service() {
    log_step "创建 systemd 服务..."
    
    SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
    
    cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Invoice Service
After=network.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_USER
WorkingDirectory=$APP_DIR
EnvironmentFile=$APP_DIR/config/env.conf
ExecStart=/usr/bin/java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dspring.profiles.active=prod \
    -Dspring.config.additional-location=file:$APP_DIR/config/application-prod.yml \
    -Dfile.encoding=UTF-8 \
    $APP_DIR/bin/${APP_NAME}-${APP_VERSION}.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=always
RestartSec=10
StandardOutput=append:$APP_DIR/logs/invoice-service.log
StandardError=append:$APP_DIR/logs/invoice-service-error.log

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable "${SERVICE_NAME}" > /dev/null 2>&1
    log_success "systemd 服务创建并已设置开机自启"
}

# 停止服务
stop_service() {
    log_step "停止现有服务..."
    
    # 检查服务是否存在
    if systemctl list-unit-files | grep -q "^${SERVICE_NAME}.service"; then
        if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
            log_info "正在停止服务 ${SERVICE_NAME}..."
            systemctl stop "${SERVICE_NAME}" > /dev/null 2>&1
            sleep 2
            
            if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
                log_warning "服务停止失败，尝试强制停止..."
                systemctl kill -s KILL "${SERVICE_NAME}" > /dev/null 2>&1 || true
                sleep 1
            fi
            
            if ! systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
                log_success "服务已停止"
            else
                log_warning "服务可能仍在运行，请手动检查"
            fi
        else
            log_info "服务未运行，跳过停止操作"
        fi
    else
        log_info "服务未安装，跳过停止操作"
    fi
    
    # 检查端口占用
    if ! check_port "$PORT"; then
        log_warning "端口 $PORT 被占用，部署可能会失败"
    fi
}

# 启动服务
start_service() {
    if [ "$AUTO_START" != "true" ]; then
        log_info "跳过服务启动（AUTO_START=false）"
        return 0
    fi
    
    log_step "启动服务..."
    
    # 检查服务文件是否存在
    if [ ! -f "/etc/systemd/system/${SERVICE_NAME}.service" ]; then
        log_error "服务文件不存在: /etc/systemd/system/${SERVICE_NAME}.service"
        return 1
    fi
    
    # 重新加载 systemd
    systemctl daemon-reload
    
    # 启动服务
    if ! systemctl start "${SERVICE_NAME}"; then
        log_error "服务启动失败"
        log_info "查看服务状态:"
        systemctl status "${SERVICE_NAME}" --no-pager -l | head -n 20
        log_info "查看详细日志: journalctl -u ${SERVICE_NAME} -n 50"
        log_info "查看应用日志: tail -50 $APP_DIR/logs/invoice-service.log"
        return 1
    fi
    
    # 等待服务启动
    sleep 5
    
    # 检查服务状态
    if systemctl is-active --quiet "${SERVICE_NAME}"; then
        log_success "服务启动成功"
        systemctl status "${SERVICE_NAME}" --no-pager -l | head -n 15
    else
        log_error "服务启动后未运行"
        log_info "服务状态:"
        systemctl status "${SERVICE_NAME}" --no-pager -l | head -n 20
        log_info "查看详细日志: journalctl -u ${SERVICE_NAME} -n 50"
        log_info "查看应用日志: tail -50 $APP_DIR/logs/invoice-service.log"
        
        # 检查进程是否存在
        local java_pid=$(pgrep -f "${APP_NAME}-${APP_VERSION}.jar" | head -n 1)
        if [ -n "$java_pid" ]; then
            log_info "发现 Java 进程 (PID: $java_pid)，但服务状态异常"
        else
            log_warning "未找到 Java 进程，服务可能启动失败"
        fi
        
        return 1
    fi
}

# 测试服务健康状态
test_service_health() {
    local test_url=$1
    local service_name=$2
    local max_retries=10
    local interval=3
    
    log_info "测试 $service_name 健康状态..."
    log_info "测试地址: $test_url"
    
    local retry_count=0
    local success=false
    
    while [ $retry_count -lt $max_retries ]; do
        if command -v curl &> /dev/null; then
            if curl -s --max-time 5 --fail "$test_url" > /dev/null 2>&1; then
                success=true
                break
            fi
        elif command -v wget &> /dev/null; then
            if wget -q --spider --timeout=5 "$test_url" > /dev/null 2>&1; then
                success=true
                break
            fi
        fi
        
        retry_count=$((retry_count + 1))
        if [ $retry_count -lt $max_retries ]; then
            log_info "等待服务启动... ($retry_count/$max_retries)"
            sleep $interval
        fi
    done
    
    if [ "$success" = true ]; then
        log_success "$service_name 健康检查通过"
        return 0
    else
        log_warning "$service_name 健康检查失败（已重试 $max_retries 次）"
        return 1
    fi
}

# 测试后端 API
test_backend_api() {
    log_step "测试后端 API 连接..."
    
    LOCAL_IP=$(get_local_ip)
    
    # 先测试根路径（更通用）
    local test_urls=(
        "http://localhost:$PORT/"
        "http://localhost:$PORT/api/health"
    )
    
    local success=false
    for test_url in "${test_urls[@]}"; do
        if test_service_health "$test_url" "后端 API" 10 3; then
            log_info "后端 API 可访问: $test_url"
            success=true
            break
        fi
    done
    
    if [ "$success" = false ]; then
        log_warning "后端 API 连接测试失败，但服务可能仍在启动中"
        log_info "请检查服务状态和日志："
        log_info "  systemctl status ${SERVICE_NAME}"
        log_info "  journalctl -u ${SERVICE_NAME} -n 50"
        log_info "  tail -f $APP_DIR/logs/invoice-service.log"
        log_info "手动测试: curl http://localhost:$PORT/"
        return 1
    fi
    
    return 0
}

# 测试前端服务
test_frontend_service() {
    if [ "$DEPLOY_FRONTEND" != "true" ]; then
        return 0
    fi
    
    log_step "测试前端服务连接..."
    
    # 首先检查 Nginx 是否运行
    if ! systemctl is-active --quiet nginx; then
        log_error "Nginx 服务未运行"
        log_info "请启动 Nginx: systemctl start nginx"
        return 1
    fi
    
    # 检查端口是否在监听
    if ! check_port "$FRONTEND_PORT"; then
        log_info "端口 $FRONTEND_PORT 正在监听"
    else
        log_error "端口 $FRONTEND_PORT 未在监听"
        log_info "请检查 Nginx 配置和日志"
        return 1
    fi
    
    LOCAL_IP=$(get_local_ip)
    local test_urls=(
        "http://localhost:$FRONTEND_PORT"
        "http://localhost:$FRONTEND_PORT/index.html"
    )
    
    local success=false
    for test_url in "${test_urls[@]}"; do
        log_info "测试连接: $test_url"
        if test_service_health "$test_url" "前端服务" 10 2; then
            log_info "前端服务可访问: $test_url"
            success=true
            break
        fi
    done
    
    if [ "$success" = false ]; then
        log_warning "前端服务连接测试失败"
        log_info "诊断信息:"
        log_info "  Nginx 状态: systemctl status nginx"
        log_info "  端口监听: netstat -tuln | grep :$FRONTEND_PORT"
        log_info "  Nginx 错误日志: tail -20 /var/log/nginx/error.log"
        log_info "  前端错误日志: tail -20 /var/log/nginx/invoice-frontend-error.log"
        log_info "  前端目录: ls -la $FRONTEND_DIR"
        log_info "  手动测试: curl -I http://localhost:$FRONTEND_PORT"
        return 1
    fi
    
    return 0
}

# 显示部署信息
show_deployment_info() {
    LOCAL_IP=$(get_local_ip)
    
    echo ""
    echo "=========================================="
    log_success "部署信息"
    echo "=========================================="
    echo ""
    echo "应用信息:"
    echo "  应用名称: $APP_NAME"
    echo "  应用版本: $APP_VERSION"
    echo "  应用目录: $APP_DIR"
    echo "  日志目录: $APP_DIR/logs"
    echo ""
    echo "访问地址:"
    echo "  后端 API:"
    echo "    本地访问: http://localhost:$PORT"
    if [ -n "$LOCAL_IP" ]; then
        echo "    网络访问: http://$LOCAL_IP:$PORT"
    fi
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        echo "  前端界面:"
        echo "    本地访问: http://localhost:$FRONTEND_PORT"
        if [ -n "$LOCAL_IP" ]; then
            echo "    网络访问: http://$LOCAL_IP:$FRONTEND_PORT"
        fi
    fi
    echo ""
    echo "服务管理命令:"
    echo "  启动服务: systemctl start ${SERVICE_NAME}"
    echo "  停止服务: systemctl stop ${SERVICE_NAME}"
    echo "  重启服务: systemctl restart ${SERVICE_NAME}"
    echo "  查看状态: systemctl status ${SERVICE_NAME}"
    echo "  查看日志: journalctl -u ${SERVICE_NAME} -f"
    echo "  应用日志: tail -f $APP_DIR/logs/invoice-service.log"
    echo ""
    echo "配置文件:"
    echo "  生产配置: $APP_DIR/config/application-prod.yml"
    echo "  环境变量: $APP_DIR/config/env.conf"
    echo ""
    echo "测试命令:"
    echo "  测试后端: curl http://localhost:$PORT/"
    echo "  测试后端健康: curl http://localhost:$PORT/api/health"
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        echo "  测试前端: curl http://localhost:$FRONTEND_PORT"
    fi
    echo ""
    echo "故障排查:"
    echo "  查看服务状态: systemctl status ${SERVICE_NAME}"
    echo "  查看系统日志: journalctl -u ${SERVICE_NAME} -n 100"
    echo "  查看应用日志: tail -100 $APP_DIR/logs/invoice-service.log"
    echo "  查看错误日志: tail -100 $APP_DIR/logs/invoice-service-error.log"
    echo ""
    if [ -z "$ARK_API_KEY" ]; then
        log_warning "提示: 请配置 ARK_API_KEY 环境变量或编辑 $APP_DIR/config/env.conf"
    fi
    echo ""
}

# ============================================
# 主函数
# ============================================
main() {
    echo ""
    echo "=========================================="
    echo "  Invoice Service 运行时部署脚本"
    echo "=========================================="
    echo ""
    
    # 显示配置信息
    log_info "部署配置:"
    echo "  应用目录: $APP_DIR"
    echo "  服务端口: $PORT"
    echo "  监听地址: $SERVER_ADDRESS"
    echo "  自动启动: $AUTO_START"
    echo "  部署前端: $DEPLOY_FRONTEND"
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        echo "  前端端口: $FRONTEND_PORT"
    fi
    echo ""
    
    # 检查权限和系统
    check_root
    check_system
    
    # 检查构建产物
    check_build_artifacts
    echo ""
    
    # 更新包管理器
    update_package_manager
    
    # 安装运行时依赖
    log_step "开始安装运行时依赖..."
    install_java
    
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        install_nginx
    fi
    echo ""
    
    # 停止现有服务（如果存在）
    stop_service
    echo ""
    
    # 配置访问地址
    configure_server_address
    echo ""
    
    # 创建用户和目录
    create_user
    create_directories
    echo ""
    
    # 创建生产配置
    create_prod_config
    
    # 部署文件
    deploy_files
    echo ""
    
    # 创建 systemd 服务
    create_systemd_service
    echo ""
    
    # 启动服务（允许失败，继续部署前端）
    if ! start_service; then
        log_warning "后端服务启动失败，但将继续部署前端（如果启用）"
    fi
    echo ""
    
    # 等待服务完全启动
    log_info "等待服务完全启动（30秒）..."
    sleep 30
    
    # 检查服务是否仍在运行
    if ! systemctl is-active --quiet "${SERVICE_NAME}"; then
        log_error "服务启动后停止运行"
        log_info "查看错误日志:"
        journalctl -u "${SERVICE_NAME}" -n 50 --no-pager | tail -30
        log_info "查看应用日志:"
        tail -50 "$APP_DIR/logs/invoice-service.log" 2>/dev/null || echo "日志文件不存在"
        log_warning "后端服务未正常运行，但将继续部署前端（如果启用）"
    else
        # 测试后端 API 连接
        test_backend_api
        echo ""
    fi
    
    # 部署前端（无论后端状态如何都继续部署）
    echo ""
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        log_step "开始部署前端..."
        deploy_frontend
        local frontend_deploy_result=$?
        echo ""
        
        if [ $frontend_deploy_result -eq 0 ]; then
            # 测试前端服务连接
            test_frontend_service
        else
            log_error "前端部署失败，请检查错误信息"
        fi
        echo ""
    else
        log_info "跳过前端部署（DEPLOY_FRONTEND=false）"
    fi
    
    # 显示部署信息
    show_deployment_info
    
    # 最终状态检查
    echo ""
    log_step "最终状态检查..."
    if systemctl is-active --quiet "${SERVICE_NAME}"; then
        log_success "后端服务运行正常"
    else
        log_error "后端服务未运行，请检查日志并手动启动"
        log_info "启动命令: systemctl start ${SERVICE_NAME}"
        log_info "查看日志: journalctl -u ${SERVICE_NAME} -f"
    fi
    
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        if systemctl is-active --quiet nginx; then
            log_success "Nginx 服务运行正常"
            
            # 检查端口监听
            if ! check_port "$FRONTEND_PORT"; then
                log_success "端口 $FRONTEND_PORT 正在监听"
            else
                log_warning "端口 $FRONTEND_PORT 未在监听"
                log_info "检查命令: netstat -tuln | grep :$FRONTEND_PORT"
            fi
            
            # 检查前端文件
            if [ -f "/opt/invoice-frontend/index.html" ]; then
                log_success "前端文件存在"
            else
                log_warning "前端文件不存在: /opt/invoice-frontend/index.html"
            fi
        else
            log_error "Nginx 服务未运行"
            log_info "启动命令: systemctl start nginx"
            log_info "查看状态: systemctl status nginx"
            log_info "查看日志: journalctl -u nginx -n 50"
            log_info "查看错误: tail -50 /var/log/nginx/error.log"
        fi
    fi
    
    echo ""
    log_success "部署脚本执行完成！"
}

# 运行主函数
main

                                                                                                                                                                                                                                                                                                                                                                                                                            