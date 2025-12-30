# Linux 服务器部署指南

本文档介绍如何在 Linux 服务器上部署 Invoice Service。

## 前置要求

- Linux 服务器（Ubuntu/Debian/CentOS/RHEL/Fedora）
- Root 权限
- 网络连接（用于下载依赖）

## 代码上传到服务器

### 方法零：离线部署（无法连接服务器时）

如果无法通过 SSH 连接服务器，可以使用打包方式：

**1. 在本地打包项目：**
```powershell
# 在项目根目录运行
.\package-for-deployment.ps1
```

这会创建一个压缩包（如 `invoice-service-deploy-1.0.0-20231229-120000.zip`），包含所有部署所需的文件。

**2. 传输压缩包到服务器：**
- 使用 U盘
- 使用 FTP/SFTP 客户端（如 FileZilla）
- 使用其他文件传输方式

**3. 在服务器上解压并部署：**
```bash
# 解压文件
unzip invoice-service-deploy-*.zip -d /tmp/
cd /tmp/invoice-service-deploy-*

# 运行部署脚本
sudo bash deploy.sh
```

详细说明请查看压缩包内的 `DEPLOY-INSTRUCTIONS.md`。

### 方法一：使用 PowerShell 上传脚本（推荐 Windows 用户）

项目提供了两个上传脚本：

**简化版（推荐）：**
```powershell
# 在项目根目录运行
.\upload-simple.ps1
```

**完整版（支持多种传输方式）：**
```powershell
# 在项目根目录运行
.\upload-to-server.ps1
```

脚本会自动：
- ✅ 通过跳板机连接到目标服务器
- ✅ 排除不需要的文件（node_modules, target, .git 等）
- ✅ 上传到服务器的 `/tmp/invoice` 目录

### 方法二：使用 SCP 命令（Linux/Mac/Git Bash）

**直接连接：**
```bash
# 上传整个项目目录
scp -r invoice/ user@server:/tmp/
```

**通过跳板机连接：**
```bash
# 使用 ProxyCommand
scp -r -o ProxyCommand="ssh -W %h:%p jumpuser@jumphost" \
    invoice/ targetuser@targetserver:/tmp/
```

**使用 SSH 密钥：**
```bash
scp -i ~/.ssh/id_rsa -r invoice/ user@server:/tmp/
```

### 方法三：使用 Git（如果服务器已安装 Git）

**在服务器上克隆：**
```bash
# SSH 登录服务器后
cd /tmp
git clone <your-repo-url> invoice
cd invoice
```

**或使用 SSH 密钥克隆私有仓库：**
```bash
git clone git@github.com:username/invoice.git
```

### 方法四：使用压缩包传输

**Windows PowerShell：**
```powershell
# 1. 创建压缩包（排除不需要的文件）
tar -czf invoice.tar.gz --exclude="node_modules" --exclude="target" --exclude=".git" .

# 2. 上传压缩包
scp invoice.tar.gz user@server:/tmp/

# 3. SSH 登录服务器解压
ssh user@server
cd /tmp
tar -xzf invoice.tar.gz -C invoice
```

**Linux/Mac：**
```bash
# 创建压缩包
tar -czf invoice.tar.gz --exclude="node_modules" --exclude="target" --exclude=".git" .

# 上传
scp invoice.tar.gz user@server:/tmp/

# 解压
ssh user@server "cd /tmp && tar -xzf invoice.tar.gz -C invoice"
```

## 部署方式

### 方式一：完整部署脚本（推荐生产环境）

完整部署脚本会自动安装依赖、创建用户、配置 systemd 服务等。

```bash
# 1. 上传项目文件到服务器（使用上面的方法）

# 2. SSH 登录服务器
ssh user@server

# 3. 进入项目目录
cd /tmp/invoice

# 4. 设置 ARK_API_KEY（可选，脚本会提示输入）
export ARK_API_KEY="your_api_key_here"

# 5. 运行部署脚本
sudo bash deploy.sh
```

部署脚本会执行以下操作：
- ✅ 检查并安装 Java 17
- ✅ 检查并安装 Maven
- ✅ 创建应用用户 `invoice`
- ✅ 创建应用目录 `/opt/invoice-service`
- ✅ 构建项目
- ✅ 部署文件
- ✅ 创建 systemd 服务（可选）
- ✅ 配置防火墙（可选）

### 方式二：快速部署脚本（适合开发/测试）

快速部署脚本仅构建和启动应用，不安装系统依赖。

```bash
# 1. 确保已安装 Java 17 和 Maven
java -version  # 需要 Java 17+
mvn -version   # 需要 Maven 3.x+

# 2. 设置环境变量
export ARK_API_KEY="your_api_key_here"

# 3. 运行快速部署脚本
bash deploy-quick.sh
```

### 方式三：手动部署

如果不想使用脚本，可以手动执行以下步骤：

#### 1. 安装依赖

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven
```

**CentOS/RHEL:**
```bash
sudo yum install -y java-17-openjdk java-17-openjdk-devel maven
```

**Fedora:**
```bash
sudo dnf install -y java-17-openjdk java-17-openjdk-devel maven
```

#### 2. 构建项目

```bash
export ARK_API_KEY="your_api_key_here"
mvn clean package -DskipTests
```

#### 3. 创建应用目录

```bash
sudo mkdir -p /opt/invoice-service/{bin,logs,uploads,outputs,temp,config}
sudo useradd -r -s /bin/bash -d /opt/invoice-service invoice
sudo chown -R invoice:invoice /opt/invoice-service
```

#### 4. 部署文件

```bash
sudo cp target/invoice-service-1.0.0.jar /opt/invoice-service/bin/
sudo cp deploy.sh /opt/invoice-service/bin/  # 可选
```

#### 5. 创建 systemd 服务

创建 `/etc/systemd/system/invoice-service.service`:

```ini
[Unit]
Description=Invoice Service
After=network.target

[Service]
Type=simple
User=invoice
Group=invoice
WorkingDirectory=/opt/invoice-service
Environment="ARK_API_KEY=your_api_key_here"
ExecStart=/usr/bin/java -jar \
    -Xms512m \
    -Xmx2048m \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    /opt/invoice-service/bin/invoice-service-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=append:/opt/invoice-service/logs/invoice-service.log
StandardError=append:/opt/invoice-service/logs/invoice-service-error.log

[Install]
WantedBy=multi-user.target
```

#### 6. 启动服务

```bash
sudo systemctl daemon-reload
sudo systemctl enable invoice-service
sudo systemctl start invoice-service
sudo systemctl status invoice-service
```

## 服务管理

### 使用 systemd（推荐）

```bash
# 启动服务
sudo systemctl start invoice-service

# 停止服务
sudo systemctl stop invoice-service

# 重启服务
sudo systemctl restart invoice-service

# 查看状态
sudo systemctl status invoice-service

# 查看日志
sudo journalctl -u invoice-service -f
# 或
tail -f /opt/invoice-service/logs/invoice-service.log

# 设置开机自启
sudo systemctl enable invoice-service
```

### 使用启动脚本

如果使用部署脚本创建的启动脚本：

```bash
# 启动
sudo -u invoice /opt/invoice-service/bin/start.sh

# 停止
sudo -u invoice /opt/invoice-service/bin/stop.sh
```

## 配置说明

### 环境变量

- `ARK_API_KEY`: 火山引擎 API Key（必需）
- `PORT`: 服务端口（默认 8080）

### 应用配置

配置文件位置：`/opt/invoice-service/config/env.conf`

```bash
ARK_API_KEY=your_api_key_here
APP_DIR=/opt/invoice-service
PORT=8080
```

### 目录结构

```
/opt/invoice-service/
├── bin/                    # 可执行文件
│   ├── invoice-service-1.0.0.jar
│   ├── start.sh
│   └── stop.sh
├── config/                 # 配置文件
│   └── env.conf
├── logs/                   # 日志文件
│   └── invoice-service.log
├── uploads/               # 上传文件目录
├── outputs/               # 输出文件目录
└── temp/                  # 临时文件目录
```

## 防火墙配置

### firewalld (CentOS/RHEL/Fedora)

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### ufw (Ubuntu/Debian)

```bash
sudo ufw allow 8080/tcp
sudo ufw reload
```

## 验证部署

部署完成后，可以通过以下方式验证：

```bash
# 1. 检查服务状态
sudo systemctl status invoice-service

# 2. 检查端口监听
sudo netstat -tlnp | grep 8080
# 或
sudo ss -tlnp | grep 8080

# 3. 测试 API
curl http://localhost:8080/health
# 或
curl http://your-server-ip:8080/health
```

## 常见问题

### 1. Java 版本不匹配

确保安装的是 Java 17：

```bash
java -version
# 应该显示 openjdk version "17.x.x"
```

### 2. 端口被占用

检查端口占用：

```bash
sudo lsof -i :8080
# 或
sudo netstat -tlnp | grep 8080
```

修改端口：

```bash
# 编辑配置文件
sudo vi /opt/invoice-service/config/env.conf
# 修改 PORT=8080 为其他端口

# 重启服务
sudo systemctl restart invoice-service
```

### 3. 权限问题

确保应用用户有正确的权限：

```bash
sudo chown -R invoice:invoice /opt/invoice-service
sudo chmod +x /opt/invoice-service/bin/*.sh
```

### 4. 日志查看

```bash
# 实时查看日志
tail -f /opt/invoice-service/logs/invoice-service.log

# 查看错误日志
tail -f /opt/invoice-service/logs/invoice-service-error.log

# 使用 journalctl
sudo journalctl -u invoice-service -f
```

### 5. 内存不足

如果服务器内存较小，可以调整 JVM 参数：

编辑 systemd 服务文件：

```bash
sudo vi /etc/systemd/system/invoice-service.service
```

修改 `-Xms512m -Xmx2048m` 为更小的值，例如：
- `-Xms256m -Xmx1024m` (1GB 内存)
- `-Xms128m -Xmx512m` (512MB 内存)

然后重新加载并重启：

```bash
sudo systemctl daemon-reload
sudo systemctl restart invoice-service
```

## 更新部署

更新应用时：

```bash
# 1. 停止服务
sudo systemctl stop invoice-service

# 2. 备份旧版本
sudo cp /opt/invoice-service/bin/invoice-service-1.0.0.jar /opt/invoice-service/bin/invoice-service-1.0.0.jar.bak

# 3. 构建新版本
cd /path/to/project
mvn clean package -DskipTests

# 4. 部署新版本
sudo cp target/invoice-service-1.0.0.jar /opt/invoice-service/bin/

# 5. 启动服务
sudo systemctl start invoice-service

# 6. 检查状态
sudo systemctl status invoice-service
```

## 离线部署（无网络连接）

如果服务器无法连接互联网，需要手动安装依赖：

### 1. 准备依赖包

在有网络的机器上下载以下依赖：

**Java 17:**
- Ubuntu/Debian: 下载 `.deb` 包
- CentOS/RHEL: 下载 `.rpm` 包
- 或下载 OpenJDK 17 的 tar.gz 包

**Maven:**
- 下载 Maven 二进制包：`apache-maven-3.9.5-bin.tar.gz`

### 2. 手动安装 Java

**使用 tar.gz 包：**
```bash
# 解压
tar -xzf openjdk-17_linux-x64_bin.tar.gz -C /opt/

# 设置环境变量
export JAVA_HOME=/opt/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

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

### 3. 手动安装 Maven

```bash
# 解压
tar -xzf apache-maven-3.9.5-bin.tar.gz -C /opt/

# 设置环境变量
export MAVEN_HOME=/opt/apache-maven-3.9.5
export PATH=$MAVEN_HOME/bin:$PATH

# 验证
mvn -version
```

### 4. 部署应用

```bash
# 进入项目目录
cd /tmp/invoice-service-deploy-*

# 设置环境变量（如果需要）
export JAVA_HOME=/opt/jdk-17
export MAVEN_HOME=/opt/apache-maven-3.9.5
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

# 构建项目
mvn clean package -DskipTests

# 设置 API Key
export ARK_API_KEY="your_api_key_here"

# 创建目录
sudo mkdir -p /opt/invoice-service/{bin,logs,uploads,outputs,temp,config}
sudo useradd -r -s /bin/bash -d /opt/invoice-service invoice 2>/dev/null || true
sudo chown -R invoice:invoice /opt/invoice-service

# 复制 JAR 文件
sudo cp target/invoice-service-1.0.0.jar /opt/invoice-service/bin/

# 手动启动（不使用 systemd）
cd /opt/invoice-service
sudo -u invoice java -jar bin/invoice-service-1.0.0.jar
```

### 5. 创建启动脚本

创建 `/opt/invoice-service/start.sh`:
```bash
#!/bin/bash
cd /opt/invoice-service
export JAVA_HOME=/opt/jdk-17
export PATH=$JAVA_HOME/bin:$PATH
export ARK_API_KEY="your_api_key_here"
nohup java -jar bin/invoice-service-1.0.0.jar > logs/app.log 2>&1 &
echo $! > bin/app.pid
```

## 安全建议

1. **不要在生产环境中使用默认配置**
2. **定期更新系统和依赖**
3. **配置防火墙规则，只开放必要端口**
4. **使用 HTTPS（配置反向代理如 Nginx）**
5. **定期备份应用数据和配置**
6. **监控日志和系统资源**

## 技术支持

如遇到问题，请检查：
- 日志文件：`/opt/invoice-service/logs/`
- 系统日志：`journalctl -u invoice-service`
- 应用状态：`systemctl status invoice-service`


