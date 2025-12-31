# 部署相关文件说明

## 📦 打包和部署文件

### Windows 端文件

| 文件 | 说明 | 使用方法 |
|------|------|----------|
| `打包部署文件.bat` | 一键打包脚本（最简单） | 双击运行 |
| `package-for-deployment.ps1` | PowerShell 打包脚本 | `.\package-for-deployment.ps1` |
| `upload-simple.ps1` | 简化上传脚本 | `.\upload-simple.ps1` |
| `upload-to-server.ps1` | 完整上传脚本 | `.\upload-to-server.ps1` |

### Linux 服务器端文件

| 文件 | 说明 | 使用方法 |
|------|------|----------|
| `deploy.sh` | 完整部署脚本 | `sudo bash deploy.sh` |
| `deploy-quick.sh` | 快速部署脚本 | `bash deploy-quick.sh` |

## 🚀 快速开始

### 场景1：无法连接服务器（离线部署）

1. **打包：** 运行 `打包部署文件.bat`
2. **传输：** 将生成的 zip 文件传到服务器（U盘、FTP等）
3. **部署：** 在服务器上解压并运行 `sudo bash deploy.sh`

详细说明：查看 [快速部署指南.md](快速部署指南.md)

### 场景2：可以连接服务器（在线部署）

1. **上传：** 运行 `.\upload-simple.ps1`
2. **部署：** SSH 登录服务器，运行 `sudo bash deploy.sh`

详细说明：查看 [UPLOAD-GUIDE.md](UPLOAD-GUIDE.md)

## 📚 文档说明

| 文档 | 内容 | 适用场景 |
|------|------|----------|
| [快速部署指南.md](快速部署指南.md) | 快速上手指南 | 所有场景 |
| [DEPLOY.md](DEPLOY.md) | 完整部署文档 | 生产环境 |
| [OFFLINE-DEPLOY.md](OFFLINE-DEPLOY.md) | 离线部署指南 | 无网络环境 |
| [UPLOAD-GUIDE.md](UPLOAD-GUIDE.md) | 代码上传指南 | 可连接服务器 |

## 🎯 选择部署方式

### 方式一：完整部署（推荐生产环境）

**特点：**
- ✅ 自动安装所有依赖
- ✅ 创建 systemd 服务
- ✅ 配置防火墙
- ✅ 适合生产环境

**使用：**
```bash
sudo bash deploy.sh
```

### 方式二：快速部署（开发/测试）

**特点：**
- ✅ 快速启动
- ✅ 需要预先安装 Java 和 Maven
- ✅ 适合开发测试

**使用：**
```bash
bash deploy-quick.sh
```

### 方式三：手动部署（完全离线）

**特点：**
- ✅ 完全离线
- ✅ 需要手动安装依赖
- ✅ 适合内网环境

**使用：**
参考 [OFFLINE-DEPLOY.md](OFFLINE-DEPLOY.md)

## ⚙️ 配置要求

### 必需配置

- `ARK_API_KEY` - 火山引擎 API 密钥

### 可选配置

- `PORT` - 服务端口（默认 8080）
- `JAVA_HOME` - Java 安装路径
- `MAVEN_HOME` - Maven 安装路径

## 🔍 故障排查

### 打包问题

- 确保在项目根目录运行
- 检查 PowerShell 执行策略
- 查看错误信息

### 部署问题

- 查看日志：`tail -f /opt/invoice-service/logs/invoice-service.log`
- 检查 Java 版本：`java -version`
- 检查端口占用：`netstat -tlnp | grep 8080`

### 服务问题

- 查看服务状态：`sudo systemctl status invoice-service`
- 查看系统日志：`journalctl -u invoice-service -f`

## 📞 获取帮助

1. 查看相关文档
2. 检查日志文件
3. 查看常见问题部分

## 🎉 部署成功标志

- ✅ 服务状态：`systemctl status invoice-service` 显示 `active (running)`
- ✅ 端口监听：`netstat -tlnp | grep 8080` 显示监听
- ✅ API 测试：`curl http://localhost:8080/health` 返回正常

---

**提示：** 首次部署建议使用 `deploy.sh` 完整部署脚本，它会自动处理所有配置。








