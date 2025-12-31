# 离线部署指南

当服务器无法连接互联网时，使用此指南进行离线部署。

## 📦 第一步：打包项目

在本地（有网络的机器）运行打包脚本：

```powershell
.\package-for-deployment.ps1
```

这会生成一个压缩包，例如：`invoice-service-deploy-1.0.0-20231229-120000.zip`

## 📤 第二步：传输到服务器

将压缩包传输到服务器，可以使用：

- ✅ U盘
- ✅ FTP/SFTP 客户端（如 FileZilla）
- ✅ 内网文件共享
- ✅ 其他文件传输方式

## 📥 第三步：在服务器上解压

```bash
# 解压文件
unzip invoice-service-deploy-*.zip -d /tmp/
cd /tmp/invoice-service-deploy-*

# 查看文件
ls -la
```

## 🔧 第四步：安装依赖（离线）

### 方式一：使用预编译包（推荐）

**1. 准备依赖包**

在有网络的机器上下载：

- **Java 17**: 
  - Ubuntu/Debian: [OpenJDK 17 .deb](https://adoptium.net/)
  - CentOS/RHEL: [OpenJDK 17 .rpm](https://adoptium.net/)
  - 或下载 [OpenJDK 17 tar.gz](https://adoptium.net/)

- **Maven**: 
  - [Maven 3.9.5 Binary](https://maven.apache.org/download.cgi)

**2. 安装 Java**

**使用 tar.gz（通用）：**
```bash
# 解压
sudo tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz -C /opt/

# 重命名（可选）
sudo mv /opt/jdk-17.0.9+9 /opt/jdk-17

# 设置环境变量（临时）
export JAVA_HOME=/opt/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

# 设置环境变量（永久）
echo 'export JAVA_HOME=/opt/jdk-17' | sudo tee -a /etc/profile
echo 'export PATH=$JAVA_HOME/bin:$PATH' | sudo tee -a /etc/profile
source /etc/profile

# 验证
java -version
```

**使用 deb 包（Ubuntu/Debian）：**
```bash
sudo dpkg -i openjdk-17-jdk_*.deb
```

**使用 rpm 包（CentOS/RHEL）：**
```bash
sudo rpm -ivh java-17-openjdk-*.rpm
```

**3. 安装 Maven**

```bash
# 解压
sudo tar -xzf apache-maven-3.9.5-bin.tar.gz -C /opt/

# 设置环境变量（临时）
export MAVEN_HOME=/opt/apache-maven-3.9.5
export PATH=$MAVEN_HOME/bin:$PATH

# 设置环境变量（永久）
echo 'export MAVEN_HOME=/opt/apache-maven-3.9.5' | sudo tee -a /etc/profile
echo 'export PATH=$MAVEN_HOME/bin:$PATH' | sudo tee -a /etc/profile
source /etc/profile

# 验证
mvn -version
```

### 方式二：使用系统包管理器（如果有本地仓库）

如果服务器有本地包仓库或可以访问内网仓库：

```bash
# Ubuntu/Debian（如果有本地 apt 仓库）
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven

# CentOS/RHEL（如果有本地 yum 仓库）
sudo yum install -y java-17-openjdk java-17-openjdk-devel maven
```

## 🚀 第五步：部署应用

### 方式一：使用部署脚本（需要网络下载依赖）

如果服务器可以连接互联网：

```bash
cd /tmp/invoice-service-deploy-*
sudo bash deploy.sh
```

### 方式二：手动部署（完全离线）

```bash
cd /tmp/invoice-service-deploy-*

# 1. 设置环境变量
export JAVA_HOME=/opt/jdk-17
export MAVEN_HOME=/opt/apache-maven-3.9.5
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

# 2. 构建项目
mvn clean package -DskipTests

# 3. 创建应用目录
sudo mkdir -p /opt/invoice-service/{bin,logs,uploads,outputs,temp,config}
sudo useradd -r -s /bin/bash -d /opt/invoice-service invoice 2>/dev/null || true
sudo chown -R invoice:invoice /opt/invoice-service

# 4. 复制 JAR 文件
sudo cp target/invoice-service-1.0.0.jar /opt/invoice-service/bin/

# 5. 创建环境配置文件
sudo tee /opt/invoice-service/config/env.conf > /dev/null <<EOF
ARK_API_KEY=your_api_key_here
APP_DIR=/opt/invoice-service
PORT=8080
EOF

sudo chown invoice:invoice /opt/invoice-service/config/env.conf
sudo chmod 600 /opt/invoice-service/config/env.conf

# 6. 创建启动脚本
sudo tee /opt/invoice-service/bin/start.sh > /dev/null <<'EOF'
#!/bin/bash
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_FILE="$APP_DIR/bin/invoice-service-1.0.0.jar"
LOG_FILE="$APP_DIR/logs/invoice-service.log"
PID_FILE="$APP_DIR/bin/app.pid"

cd "$APP_DIR"

# 加载环境变量
if [ -f "$APP_DIR/config/env.conf" ]; then
    source "$APP_DIR/config/env.conf"
fi

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "应用已在运行 (PID: $PID)"
        exit 1
    fi
fi

# 启动应用
nohup java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    "$JAR_FILE" > "$LOG_FILE" 2>&1 &

echo $! > "$PID_FILE"
echo "应用已启动 (PID: $(cat $PID_FILE))"
EOF

sudo chmod +x /opt/invoice-service/bin/start.sh
sudo chown invoice:invoice /opt/invoice-service/bin/start.sh

# 7. 创建停止脚本
sudo tee /opt/invoice-service/bin/stop.sh > /dev/null <<'EOF'
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
EOF

sudo chmod +x /opt/invoice-service/bin/stop.sh
sudo chown invoice:invoice /opt/invoice-service/bin/stop.sh

# 8. 启动应用
sudo -u invoice /opt/invoice-service/bin/start.sh

# 9. 检查状态
sleep 2
ps aux | grep invoice-service
tail -f /opt/invoice-service/logs/invoice-service.log
```

## ✅ 验证部署

```bash
# 检查进程
ps aux | grep invoice-service

# 检查端口
netstat -tlnp | grep 8080
# 或
ss -tlnp | grep 8080

# 查看日志
tail -f /opt/invoice-service/logs/invoice-service.log

# 测试 API（如果服务器可以访问）
curl http://localhost:8080/health
```

## 🔄 创建 systemd 服务（可选）

如果需要开机自启和自动重启：

```bash
sudo tee /etc/systemd/system/invoice-service.service > /dev/null <<EOF
[Unit]
Description=Invoice Service
After=network.target

[Service]
Type=simple
User=invoice
Group=invoice
WorkingDirectory=/opt/invoice-service
EnvironmentFile=/opt/invoice-service/config/env.conf
Environment="JAVA_HOME=/opt/jdk-17"
ExecStart=/opt/jdk-17/bin/java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    /opt/invoice-service/bin/invoice-service-1.0.0.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=always
RestartSec=10
StandardOutput=append:/opt/invoice-service/logs/invoice-service.log
StandardError=append:/opt/invoice-service/logs/invoice-service-error.log

[Install]
WantedBy=multi-user.target
EOF

# 启用并启动服务
sudo systemctl daemon-reload
sudo systemctl enable invoice-service
sudo systemctl start invoice-service
sudo systemctl status invoice-service
```

## 📝 注意事项

1. **环境变量**: 确保 Java 和 Maven 的环境变量已正确设置
2. **权限**: 确保应用用户有正确的文件权限
3. **API Key**: 记得设置 `ARK_API_KEY` 环境变量
4. **端口**: 确保端口 8080 未被占用
5. **防火墙**: 如果需要外部访问，记得开放端口

## ❓ 常见问题

### Q: Maven 构建失败，提示找不到依赖？

**A:** Maven 需要下载依赖，如果完全离线，需要：

1. 在有网络的机器上构建，然后只上传 `target/invoice-service-1.0.0.jar`
2. 或使用 Maven 离线模式（需要预先下载所有依赖）

### Q: Java 版本不对？

**A:** 检查环境变量：

```bash
echo $JAVA_HOME
java -version
which java
```

确保使用的是 Java 17。

### Q: 应用启动失败？

**A:** 查看日志：

```bash
tail -f /opt/invoice-service/logs/invoice-service.log
```

常见原因：
- 缺少 ARK_API_KEY
- 端口被占用
- 权限问题
- Java 版本不对

## 🔗 相关文档

- [部署指南](DEPLOY.md) - 完整部署文档
- [上传指南](UPLOAD-GUIDE.md) - 代码上传说明









