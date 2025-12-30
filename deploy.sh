#!/bin/bash

# ============================================
# Invoice Service Linux 自动部署脚本
# 功能：自动安装依赖、配置访问地址、部署应用
# ============================================

set -e  # 遇到错误立即退出

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
SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"  # 0.0.0.0 表示监听所有网络接口
AUTO_START="${AUTO_START:-true}"  # 是否自动启动服务
AUTO_FIREWALL="${AUTO_FIREWALL:-false}"  # 是否自动配置防火墙（默认禁用）
ARK_API_KEY="${ARK_API_KEY:-sk-awaO6Dt5bDW2OTiRJotWuZvtkaIwnYIwAzi0Bwx49MzlZgJz}"  # 火山引擎 API 密钥
DEPLOY_FRONTEND="${DEPLOY_FRONTEND:-true}"  # 是否部署前端（默认启用）
FRONTEND_PORT="${FRONTEND_PORT:-80}"  # 前端服务端口
SKIP_BUILD="${SKIP_BUILD:-false}"  # 是否跳过构建步骤（如果已在本地构建）

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
        log_error "请使用 root 权限运行此脚本"
        log_info "使用: sudo $0"
        exit 1
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

# 获取本机 IP 地址
get_local_ip() {
    # 优先获取非回环的 IP 地址
    local ip=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' | head -1)
    if [ -z "$ip" ]; then
        ip=$(hostname -I | awk '{print $1}')
    fi
    if [ -z "$ip" ]; then
        ip=$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1' | head -1)
    fi
    echo "$ip"
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

# 获取占用端口的进程信息
get_port_process() {
    local port=$1
    if command -v lsof &> /dev/null; then
        lsof -i :$port 2>/dev/null | tail -n +2 | head -n 1
    elif command -v netstat &> /dev/null; then
        netstat -tulnp 2>/dev/null | grep ":$port " | head -n 1
    elif command -v ss &> /dev/null; then
        ss -tulnp 2>/dev/null | grep ":$port " | head -n 1
    fi
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

# 检查 Maven 版本是否满足要求
check_maven_version() {
    local mvn_version=$1
    local required_version="3.2.5"
    
    # 清理版本号（移除可能的非数字字符）
    mvn_version=$(echo "$mvn_version" | sed 's/[^0-9.]//g')
    required_version=$(echo "$required_version" | sed 's/[^0-9.]//g')
    
    # 提取版本号的主版本号和次版本号
    local mvn_major=$(echo "$mvn_version" | cut -d'.' -f1)
    local mvn_minor=$(echo "$mvn_version" | cut -d'.' -f2)
    local mvn_patch=$(echo "$mvn_version" | cut -d'.' -f3 | sed 's/[^0-9].*//')
    
    local req_major=$(echo "$required_version" | cut -d'.' -f1)
    local req_minor=$(echo "$required_version" | cut -d'.' -f2)
    local req_patch=$(echo "$required_version" | cut -d'.' -f3 | sed 's/[^0-9].*//')
    
    # 设置默认值
    mvn_major=${mvn_major:-0}
    mvn_minor=${mvn_minor:-0}
    mvn_patch=${mvn_patch:-0}
    req_major=${req_major:-0}
    req_minor=${req_minor:-0}
    req_patch=${req_patch:-0}
    
    # 比较版本号
    if [ "$mvn_major" -gt "$req_major" ] 2>/dev/null; then
        return 0
    elif [ "$mvn_major" -eq "$req_major" ] 2>/dev/null; then
        if [ "$mvn_minor" -gt "$req_minor" ] 2>/dev/null; then
            return 0
        elif [ "$mvn_minor" -eq "$req_minor" ] 2>/dev/null; then
            if [ "$mvn_patch" -ge "$req_patch" ] 2>/dev/null; then
                return 0
            fi
        fi
    fi
    return 1
}

# 查找已安装的 Maven
find_maven() {
    # 检查 /home/jmpuser/apps 目录下的所有可能的 Maven 安装
    if [ -d "/home/jmpuser/apps" ]; then
        for maven_dir in /home/jmpuser/apps/*; do
            if [ -d "$maven_dir" ] && [ -f "$maven_dir/bin/mvn" ] && [ -x "$maven_dir/bin/mvn" ]; then
                log_info "在 $maven_dir 找到 Maven"
                export MAVEN_HOME="$maven_dir"
                export PATH="$maven_dir/bin:$PATH"
                return 0
            fi
        done
    fi
    
    # 检查其他常见路径
    local maven_paths=(
        "/opt/maven/bin/mvn"
        "/usr/local/maven/bin/mvn"
        "/opt/apache-maven*/bin/mvn"
    )
    
    for mvn_path in "${maven_paths[@]}"; do
        # 使用通配符展开
        for expanded_path in $mvn_path; do
            if [ -f "$expanded_path" ] && [ -x "$expanded_path" ]; then
                local maven_home=$(dirname $(dirname "$expanded_path"))
                log_info "在 $maven_home 找到 Maven"
                export MAVEN_HOME="$maven_home"
                export PATH="$maven_home/bin:$PATH"
                return 0
            fi
        done
    done
    
    return 1
}

# 安装 Maven（从 Apache 官网）
install_maven_from_apache() {
    log_info "从 Apache 官网安装 Maven 3.9.5..."
    
    local MAVEN_VERSION="3.9.5"
    local MAVEN_HOME="/opt/maven"
    local MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    
    # 安装 wget 或 curl
    if ! command -v wget &> /dev/null && ! command -v curl &> /dev/null; then
        case $OS in
            ubuntu|debian)
                apt-get install -y -qq wget > /dev/null 2>&1
                ;;
            centos|rhel|fedora)
                if [ "$OS" = "fedora" ]; then
                    dnf install -y -q wget > /dev/null 2>&1
                else
                    yum install -y -q wget > /dev/null 2>&1
                fi
                ;;
        esac
    fi
    
    # 下载 Maven
    log_info "下载 Maven ${MAVEN_VERSION}..."
    cd /tmp
    if command -v wget &> /dev/null; then
        wget -q "$MAVEN_URL" -O maven.tar.gz || {
            log_error "Maven 下载失败，请检查网络连接"
            return 1
        }
    else
        curl -sL "$MAVEN_URL" -o maven.tar.gz || {
            log_error "Maven 下载失败，请检查网络连接"
            return 1
        }
    fi
    
    # 解压并安装
    log_info "安装 Maven..."
    rm -rf apache-maven-${MAVEN_VERSION}
    tar -xzf maven.tar.gz
    rm -rf "$MAVEN_HOME"
    mkdir -p /opt
    mv apache-maven-${MAVEN_VERSION} "$MAVEN_HOME"
    rm -f maven.tar.gz
    
    # 创建符号链接
    ln -sf "$MAVEN_HOME/bin/mvn" /usr/local/bin/mvn
    
    # 设置环境变量（添加到 /etc/profile）
    if ! grep -q "MAVEN_HOME" /etc/profile; then
        cat >> /etc/profile << EOF

# Maven Environment
export MAVEN_HOME=$MAVEN_HOME
export PATH=\$MAVEN_HOME/bin:\$PATH
EOF
    fi
    
    # 立即生效
    export MAVEN_HOME="$MAVEN_HOME"
    export PATH="$MAVEN_HOME/bin:$PATH"
    
    log_success "Maven ${MAVEN_VERSION} 安装完成"
}

# 安装 Maven
install_maven() {
    log_step "检查并安装 Maven..."
    
    local MIN_MAVEN_VERSION="3.2.5"
    local MVN_VER=""
    
    # 先尝试查找已安装的 Maven
    find_maven
    
    if command -v mvn &> /dev/null; then
        MVN_VER=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
        if check_maven_version "$MVN_VER"; then
            log_success "Maven $MVN_VER 已安装，版本满足要求（需要 $MIN_MAVEN_VERSION+）"
            return 0
        else
            log_warning "Maven 版本过低 ($MVN_VER)，需要 $MIN_MAVEN_VERSION+，将重新安装"
        fi
    else
        log_info "未检测到 Maven，开始安装..."
    fi
    
    # 先尝试从包管理器安装
    log_info "尝试从包管理器安装 Maven..."
    local install_success=false
    
    case $OS in
        ubuntu|debian)
            apt-get install -y -qq maven > /dev/null 2>&1 && install_success=true
            ;;
        centos|rhel)
            yum install -y -q maven > /dev/null 2>&1 && install_success=true
            ;;
        fedora)
            dnf install -y -q maven > /dev/null 2>&1 && install_success=true
            ;;
        *)
            log_warning "不支持的系统类型: $OS，将尝试从 Apache 官网安装"
            install_success=false
            ;;
    esac
    
    # 验证包管理器安装的版本
    if [ "$install_success" = true ] && command -v mvn &> /dev/null; then
        MVN_VER=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
        if check_maven_version "$MVN_VER"; then
            log_success "Maven $MVN_VER 安装完成（从包管理器）"
            return 0
        else
            log_warning "包管理器安装的 Maven 版本 ($MVN_VER) 仍然过低，将从 Apache 官网安装"
        fi
    fi
    
    # 如果包管理器版本不够，从 Apache 官网安装
    log_info "从 Apache 官网安装最新版本的 Maven..."
    if install_maven_from_apache; then
        # 验证安装
        if command -v mvn &> /dev/null; then
            MVN_VER=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
            log_success "Maven $MVN_VER 安装完成（从 Apache 官网）"
            return 0
        fi
    fi
    
    log_error "Maven 安装失败"
    log_info "请手动安装 Maven $MIN_MAVEN_VERSION 或更高版本"
    log_info "下载地址: https://maven.apache.org/download.cgi"
    exit 1
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
    
    # 检测本机 IP
    LOCAL_IP=$(get_local_ip)
    
    # 如果 SERVER_ADDRESS 未设置或为默认值，使用检测到的 IP
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

# 构建项目
build_project() {
    if [ "$SKIP_BUILD" = "true" ]; then
        log_info "跳过后端构建（SKIP_BUILD=true），直接检查产物..."
        if [ ! -f "$JAR_FILE" ]; then
            log_error "未找到构建产物 $JAR_FILE，请确保本地构建成功后再打包"
            exit 1
        fi
        return 0
    fi
    log_step "构建项目..."
    
    if [ ! -f "pom.xml" ]; then
        log_error "未找到 pom.xml，请确保在项目根目录运行此脚本"
        exit 1
    fi
    
    # 尝试查找已安装的 Maven（如果之前没有找到）
    if ! command -v mvn &> /dev/null; then
        find_maven
    fi
    
    # 验证 Maven 版本
    if ! command -v mvn &> /dev/null; then
        log_error "Maven 未安装，请先安装 Maven"
        exit 1
    fi
    
    MVN_VER=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
    if ! check_maven_version "$MVN_VER"; then
        log_error "Maven 版本过低 ($MVN_VER)，需要 3.2.5 或更高版本"
        log_info "请运行安装函数更新 Maven"
        exit 1
    fi
    
    log_info "使用 Maven $MVN_VER 构建项目"
    
    # 检查 ARK_API_KEY
    if [ -z "$ARK_API_KEY" ]; then
        log_warning "ARK_API_KEY 环境变量未设置"
        log_info "提示: 可以通过环境变量设置: export ARK_API_KEY='your_key'"
        log_info "或者稍后在 $APP_DIR/config/application-prod.yml 中配置"
    fi
    
    log_info "执行 Maven 构建（这可能需要几分钟）..."
    if ! mvn clean package -DskipTests; then
        log_error "Maven 构建失败"
        log_info "请检查错误信息，常见问题："
        log_info "  1. Maven 版本过低（需要 3.2.5+）"
        log_info "  2. 网络连接问题（无法下载依赖）"
        log_info "  3. Java 版本不匹配（需要 Java 17+）"
        exit 1
    fi
    
    if [ ! -f "$JAR_FILE" ]; then
        log_error "构建失败，未找到 JAR 文件: $JAR_FILE"
        exit 1
    fi
    
    log_success "项目构建完成: $JAR_FILE"
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

# 安装 Node.js
install_nodejs() {
    log_step "检查并安装 Node.js..."
    
    if command -v node &> /dev/null; then
        NODE_VER=$(node -v | sed 's/v//' | cut -d'.' -f1)
        if [ -n "$NODE_VER" ] && [ "$NODE_VER" -ge 16 ] 2>/dev/null; then
            log_success "Node.js $(node -v) 已安装，版本满足要求"
            return 0
        else
            log_warning "Node.js 版本过低 ($(node -v))，需要 16+，将重新安装"
        fi
    else
        log_info "未检测到 Node.js，开始安装..."
    fi
    
    case $OS in
        ubuntu|debian)
            curl -fsSL https://deb.nodesource.com/setup_18.x | bash - > /dev/null 2>&1 || {
                log_warning "NodeSource 仓库安装失败，尝试使用默认仓库"
                apt-get install -y -qq nodejs npm > /dev/null 2>&1
            }
            apt-get install -y -qq nodejs > /dev/null 2>&1
            ;;
        centos|rhel)
            # CentOS 7 可能需要先安装 EPEL
            if ! rpm -q epel-release > /dev/null 2>&1; then
                log_info "安装 EPEL 仓库..."
                yum install -y -q epel-release > /dev/null 2>&1 || true
            fi
            
            # 尝试安装 NodeSource 仓库
            log_info "尝试安装 NodeSource 仓库..."
            if curl -fsSL https://rpm.nodesource.com/setup_18.x | bash - > /tmp/nodesource-install.log 2>&1; then
                log_info "NodeSource 仓库安装成功"
                yum install -y -q nodejs > /dev/null 2>&1
            else
                log_warning "NodeSource 仓库安装失败，尝试使用 EPEL 仓库"
                log_info "NodeSource 安装日志:"
                tail -n 10 /tmp/nodesource-install.log | sed 's/^/  /' || true
                yum install -y -q nodejs npm > /dev/null 2>&1
            fi
            
            # 如果还是失败，尝试从源码编译安装（最后手段）
            if ! command -v node &> /dev/null; then
                log_warning "包管理器安装失败，尝试其他方法..."
                # 可以尝试使用 nvm 或其他方法
                log_info "请手动安装 Node.js 16+: https://nodejs.org/"
            fi
            ;;
        fedora)
            dnf install -y -q nodejs npm > /dev/null 2>&1
            ;;
        *)
            log_error "不支持的系统类型: $OS"
            log_info "请手动安装 Node.js 16+"
            return 1
            ;;
    esac
    
    if command -v node &> /dev/null; then
        log_success "Node.js $(node -v) 安装完成"
        if command -v npm &> /dev/null; then
            npm -v > /dev/null 2>&1 || log_warning "npm 可能未正确安装"
        fi
    else
        log_error "Node.js 安装失败"
        log_info "请手动安装 Node.js 16+: https://nodejs.org/"
        return 1
    fi
}

# 构建前端
build_frontend() {
    if [ "$SKIP_BUILD" = "true" ]; then
        log_info "跳过前端构建（SKIP_BUILD=true），直接检查产物..."
        if [ ! -d "frontend/dist" ]; then
            log_error "未找到前端构建产物 frontend/dist，请确保本地构建成功后再打包"
            return 1
        fi
        return 0
    fi
    log_step "构建前端应用..."
    
    if [ ! -d "frontend" ]; then
        log_warning "未找到 frontend 目录，跳过前端构建"
        return 1
    fi
    
    local original_dir=$(pwd)
    cd frontend
    
    # 检查 package.json
    if [ ! -f "package.json" ]; then
        log_error "未找到 frontend/package.json"
        cd "$original_dir"
        return 1
    fi
    
    # 检查 Node.js 和 npm
    if ! command -v node &> /dev/null; then
        log_error "Node.js 未安装，无法构建前端"
        cd "$original_dir"
        return 1
    fi
    
    if ! command -v npm &> /dev/null; then
        log_error "npm 未安装，无法构建前端"
        cd "$original_dir"
        return 1
    fi
    
    log_info "Node.js 版本: $(node -v)"
    log_info "npm 版本: $(npm -v)"
    
    # 安装前端依赖
    log_info "安装前端依赖..."
    if npm ci > /tmp/npm-install.log 2>&1; then
        log_success "依赖安装完成（使用 npm ci）"
    elif npm install > /tmp/npm-install.log 2>&1; then
        log_success "依赖安装完成（使用 npm install）"
    else
        log_error "依赖安装失败"
        log_info "安装日志:"
        tail -n 20 /tmp/npm-install.log | sed 's/^/  /'
        cd "$original_dir"
        return 1
    fi
    
    # 构建前端应用
    log_info "构建前端应用..."
    
    # 尝试标准构建
    if npm run build > /tmp/npm-build.log 2>&1; then
        # 检查构建输出目录
        if [ -d "dist" ] && [ "$(ls -A dist 2>/dev/null)" ]; then
            log_success "前端构建完成"
            log_info "构建产物目录: $(pwd)/dist"
            local file_count=$(find dist -type f | wc -l)
            log_info "构建文件数量: $file_count"
            ls -lh dist/ | head -n 10 | sed 's/^/  /' || true
        else
            log_error "前端构建失败，dist 目录为空或不存在"
            log_info "构建日志:"
            tail -n 30 /tmp/npm-build.log | sed 's/^/  /'
            cd "$original_dir"
            return 1
        fi
    else
        log_warning "标准构建失败，尝试跳过 TypeScript 检查..."
        log_info "构建错误日志:"
        tail -n 20 /tmp/npm-build.log | sed 's/^/  /'
        
        # 尝试直接使用 vite build（跳过 tsc）
        log_info "尝试直接构建（跳过类型检查）..."
        if npx vite build > /tmp/vite-build.log 2>&1; then
            if [ -d "dist" ] && [ "$(ls -A dist 2>/dev/null)" ]; then
                log_success "前端构建完成（跳过类型检查）"
                log_info "构建产物目录: $(pwd)/dist"
                local file_count=$(find dist -type f | wc -l)
                log_info "构建文件数量: $file_count"
            else
                log_error "Vite 构建失败，dist 目录为空"
                log_info "Vite 构建日志:"
                tail -n 30 /tmp/vite-build.log | sed 's/^/  /'
                cd "$original_dir"
                return 1
            fi
        else
            log_error "前端构建失败"
            log_info "Vite 构建日志:"
            tail -n 30 /tmp/vite-build.log | sed 's/^/  /'
            log_info ""
            log_info "常见问题排查:"
            log_info "  1. 检查 Node.js 版本是否 >= 16"
            log_info "  2. 检查 npm 依赖是否正确安装"
            log_info "  3. 检查 frontend/package.json 中的构建脚本"
            log_info "  4. 手动运行: cd frontend && npm run build"
            cd "$original_dir"
            return 1
        fi
    fi
    
    cd "$original_dir"
    log_success "前端构建完成"
}

# 安装和配置 Nginx
install_nginx() {
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

# 部署前端
deploy_frontend() {
    if [ "$DEPLOY_FRONTEND" != "true" ]; then
        log_info "跳过前端部署（DEPLOY_FRONTEND=false）"
        return 0
    fi
    
    log_step "部署前端应用..."
    
    # 安装 Node.js（如果未安装）
    if ! command -v node &> /dev/null; then
        if ! install_nodejs; then
            log_error "Node.js 安装失败，无法构建前端"
            return 1
        fi
    fi
    
    # 构建前端
    if ! build_frontend; then
        log_warning "前端构建失败，跳过前端部署"
        return 1
    fi
    
    # 安装 Nginx
    if ! install_nginx; then
        log_warning "Nginx 安装失败，跳过前端部署"
        return 1
    fi
    
    # 创建前端部署目录
    FRONTEND_DIR="/opt/invoice-frontend"
    mkdir -p "$FRONTEND_DIR"
    
    # 复制构建产物
    log_info "复制前端文件到 $FRONTEND_DIR..."
    cp -r frontend/dist/* "$FRONTEND_DIR/"
    chown -R nginx:nginx "$FRONTEND_DIR" 2>/dev/null || chown -R www-data:www-data "$FRONTEND_DIR" 2>/dev/null
    
    # 创建 Nginx 配置
    NGINX_CONF="/etc/nginx/conf.d/invoice-frontend.conf"
    log_info "创建 Nginx 配置文件..."
    
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

    # 测试 Nginx 配置
    if nginx -t > /dev/null 2>&1; then
        log_success "Nginx 配置验证通过"
    else
        log_error "Nginx 配置验证失败"
        nginx -t
        return 1
    fi
    
    # 启动并启用 Nginx
    systemctl enable nginx > /dev/null 2>&1
    systemctl restart nginx > /dev/null 2>&1
    
    if systemctl is-active --quiet nginx; then
        log_success "Nginx 启动成功"
        log_info "前端访问地址: http://$(get_local_ip):${FRONTEND_PORT}"
    else
        log_error "Nginx 启动失败"
        systemctl status nginx --no-pager | head -n 10
        return 1
    fi
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

# 配置防火墙
configure_firewall() {
    if [ "$AUTO_FIREWALL" != "true" ]; then
        log_info "跳过防火墙配置（AUTO_FIREWALL=false）"
        return 0
    fi
    
    log_step "配置防火墙..."
    
    if command -v firewall-cmd &> /dev/null; then
        # firewalld (CentOS/RHEL/Fedora)
        # 检查 FirewallD 服务是否运行
        if systemctl is-active --quiet firewalld 2>/dev/null; then
            firewall-cmd --permanent --add-port=${PORT}/tcp > /dev/null 2>&1
            firewall-cmd --reload > /dev/null 2>&1
            log_success "防火墙规则已添加 (firewalld): 端口 $PORT"
        else
            log_warning "FirewallD 服务未运行，尝试启动..."
            if systemctl start firewalld 2>/dev/null && systemctl enable firewalld 2>/dev/null; then
                sleep 2
                firewall-cmd --permanent --add-port=${PORT}/tcp > /dev/null 2>&1
                firewall-cmd --reload > /dev/null 2>&1
                log_success "FirewallD 已启动，防火墙规则已添加: 端口 $PORT"
            else
                log_warning "无法启动 FirewallD，尝试使用 iptables..."
                if command -v iptables &> /dev/null; then
                    iptables -I INPUT -p tcp --dport ${PORT} -j ACCEPT 2>/dev/null || true
                    log_success "防火墙规则已添加 (iptables): 端口 $PORT"
                    log_warning "iptables 规则需要手动保存，请运行: iptables-save > /etc/iptables/rules.v4"
                else
                    log_warning "未找到可用的防火墙工具，请手动开放端口 $PORT"
                fi
            fi
        fi
    elif command -v ufw &> /dev/null; then
        # ufw (Ubuntu/Debian)
        ufw allow ${PORT}/tcp > /dev/null 2>&1
        log_success "防火墙规则已添加 (ufw): 端口 $PORT"
    elif command -v iptables &> /dev/null; then
        # iptables
        iptables -I INPUT -p tcp --dport ${PORT} -j ACCEPT 2>/dev/null || true
        log_success "防火墙规则已添加 (iptables): 端口 $PORT"
        log_warning "iptables 规则需要手动保存，请运行: iptables-save > /etc/iptables/rules.v4"
    else
        log_warning "未检测到防火墙工具，请手动开放端口 $PORT"
    fi
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
    
    # 检查端口占用，如果是本服务的进程，尝试停止
    if ! check_port "$PORT"; then
        local port_process=$(get_port_process "$PORT")
        if [ -n "$port_process" ]; then
            log_info "检测到端口 $PORT 被占用: $port_process"
            
            # 尝试通过 PID 文件停止
            if [ -f "$APP_DIR/bin/app.pid" ]; then
                local pid=$(cat "$APP_DIR/bin/app.pid" 2>/dev/null)
                if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
                    log_info "通过 PID 文件停止进程 $pid..."
                    kill "$pid" > /dev/null 2>&1 || kill -9 "$pid" > /dev/null 2>&1 || true
                    rm -f "$APP_DIR/bin/app.pid"
                    sleep 1
                fi
            fi
            
            # 如果端口仍然被占用，尝试通过进程名停止
            if ! check_port "$PORT"; then
                local java_pid=$(lsof -ti :$PORT 2>/dev/null | head -n 1)
                if [ -n "$java_pid" ]; then
                    log_info "停止占用端口的 Java 进程 $java_pid..."
                    kill "$java_pid" > /dev/null 2>&1 || kill -9 "$java_pid" > /dev/null 2>&1 || true
                    sleep 1
                fi
            fi
        fi
    fi
    
    # 最终检查端口
    if check_port "$PORT"; then
        log_success "端口 $PORT 已释放"
    else
        log_warning "端口 $PORT 可能仍被占用，部署可能会失败"
    fi
}

# 启动服务
start_service() {
    if [ "$AUTO_START" != "true" ]; then
        log_info "跳过服务启动（AUTO_START=false）"
        return 0
    fi
    
    log_step "启动服务..."
    
    systemctl start "${SERVICE_NAME}" || {
        log_error "服务启动失败，请检查日志: journalctl -u ${SERVICE_NAME} -n 50"
        return 1
    }
    
    sleep 3
    
    if systemctl is-active --quiet "${SERVICE_NAME}"; then
        log_success "服务启动成功"
        systemctl status "${SERVICE_NAME}" --no-pager -l | head -n 10
    else
        log_error "服务启动失败"
        log_info "查看日志: journalctl -u ${SERVICE_NAME} -n 50"
        return 1
    fi
}

# 显示部署信息
show_deployment_info() {
    LOCAL_IP=$(get_local_ip)
    
    echo ""
    echo "=========================================="
    log_success "部署完成！"
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
    if [ -n "$SERVER_ADDRESS" ] && [ "$SERVER_ADDRESS" != "0.0.0.0" ]; then
        echo "    指定地址: http://$SERVER_ADDRESS:$PORT"
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
    echo "  Invoice Service 自动部署脚本"
    echo "=========================================="
    echo ""
    
    # 显示配置信息
    log_info "部署配置:"
    echo "  应用目录: $APP_DIR"
    echo "  服务端口: $PORT"
    echo "  监听地址: $SERVER_ADDRESS"
    echo "  自动启动: $AUTO_START"
    echo "  自动防火墙: $AUTO_FIREWALL"
    echo "  部署前端: $DEPLOY_FRONTEND"
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        echo "  前端端口: $FRONTEND_PORT"
    fi
    echo ""
    
    # 检查权限和系统
    check_root
    check_system
    
    # 更新包管理器
    update_package_manager
    
    # 自动安装依赖
    log_step "开始安装依赖..."
    install_java
    install_maven
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
    
    # 构建和部署
    build_project
    echo ""
    
    # 创建生产配置
    create_prod_config
    
    # 部署文件
    deploy_files
    echo ""
    
    # 创建 systemd 服务
    create_systemd_service
    echo ""
    
    # 配置防火墙
    configure_firewall
    echo ""
    
    # 启动服务
    start_service
    echo ""
    
    # 部署前端
    if [ "$DEPLOY_FRONTEND" = "true" ]; then
        deploy_frontend
        echo ""
    fi
    
    # 显示部署信息
    show_deployment_info
}

# 运行主函数
main
