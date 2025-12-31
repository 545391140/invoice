# 代码上传快速指南

## 🚀 快速开始

### Windows 用户（推荐）

在项目根目录打开 PowerShell，运行：

```powershell
.\upload-simple.ps1
```

就这么简单！脚本会自动：
- ✅ 通过跳板机连接服务器
- ✅ 排除不需要的文件
- ✅ 上传到 `/tmp/invoice` 目录

### 上传完成后

上传成功后，按照提示登录服务器：

```bash
# 1. SSH 登录服务器
ssh -i C:\Users\ruijie\Downloads\liuzhijian-jumpserver.pem `
    -o ProxyCommand="ssh -W %h:%p liuzhijian@192.168.85.179" `
    jmpuser@192.168.85.87

# 2. 进入项目目录
cd /tmp/invoice

# 3. 运行部署脚本
sudo bash deploy.sh
```

## 📋 详细说明

### 上传脚本说明

项目提供了两个上传脚本：

#### 1. `upload-simple.ps1` - 简化版（推荐）

最简单的上传方式，一键完成所有操作。

**使用方法：**
```powershell
.\upload-simple.ps1
```

**特点：**
- ✅ 简单易用
- ✅ 自动排除不需要的文件
- ✅ 适合日常使用

#### 2. `upload-to-server.ps1` - 完整版

提供多种上传方式，适合不同场景。

**使用方法：**
```powershell
.\upload-to-server.ps1
```

然后选择上传方式：
- **方式1**: SCP 直接传输（推荐）
- **方式2**: SSH 隧道传输
- **方式3**: Tar 压缩传输（适合大文件）

**特点：**
- ✅ 多种传输方式
- ✅ 适合大文件传输
- ✅ 更灵活的控制

### 排除的文件

上传脚本会自动排除以下文件和目录：

- `node_modules/` - Node.js 依赖
- `target/` - Maven 构建输出
- `.git/` - Git 版本控制
- `logs/` - 日志文件
- `uploads/` - 上传文件
- `outputs/` - 输出文件
- `temp/` - 临时文件
- `.idea/` - IDE 配置
- `.vscode/` - VS Code 配置
- `*.log` - 日志文件

### 服务器配置

当前配置（在脚本中）：

- **跳板机**: `liuzhijian@192.168.85.179`
- **目标服务器**: `jmpuser@192.168.85.87`
- **远程路径**: `/tmp/invoice`
- **密钥文件**: `C:\Users\ruijie\Downloads\liuzhijian-jumpserver.pem`

如需修改，请编辑脚本中的配置变量。

## 🔧 手动上传方法

如果脚本无法使用，可以手动上传：

### 方法1: 使用 WinSCP

1. 下载安装 [WinSCP](https://winscp.net/)
2. 配置跳板机连接
3. 拖拽项目文件夹到服务器

### 方法2: 使用 VS Code Remote SSH

1. 安装 VS Code Remote SSH 扩展
2. 配置 SSH 连接
3. 直接在服务器上编辑文件

### 方法3: 使用 Git

如果服务器可以访问 Git 仓库：

```bash
# 在服务器上
cd /tmp
git clone <your-repo-url> invoice
cd invoice
```

## ❓ 常见问题

### Q: 上传失败怎么办？

**A:** 检查以下几点：

1. **网络连接**
   ```powershell
   # 测试跳板机连接
   ssh liuzhijian@192.168.85.179
   ```

2. **密钥文件**
   ```powershell
   # 检查密钥文件是否存在
   Test-Path "C:\Users\ruijie\Downloads\liuzhijian-jumpserver.pem"
   ```

3. **SSH 配置**
   - 确保已安装 OpenSSH（Windows 10/11 自带）
   - 或使用 Git Bash

### Q: 上传速度很慢？

**A:** 尝试以下方法：

1. **使用压缩传输**（方式3）
   ```powershell
   .\upload-to-server.ps1
   # 选择方式 3
   ```

2. **只上传必要文件**
   - 手动选择需要上传的文件
   - 排除大文件目录

3. **使用 rsync**（如果服务器支持）
   ```bash
   rsync -avz --exclude='node_modules' --exclude='target' \
     invoice/ user@server:/tmp/invoice/
   ```

### Q: 如何只上传修改的文件？

**A:** 使用 Git 或 rsync：

```bash
# 使用 rsync（只同步差异）
rsync -avz --exclude='node_modules' --exclude='target' \
  invoice/ user@server:/tmp/invoice/
```

### Q: 上传后文件权限不对？

**A:** 在服务器上修复权限：

```bash
# SSH 登录服务器后
cd /tmp/invoice
sudo chown -R $USER:$USER .
chmod +x deploy.sh deploy-quick.sh
```

### Q: 如何更新代码？

**A:** 重新运行上传脚本即可：

```powershell
.\upload-simple.ps1
```

然后在服务器上：

```bash
cd /tmp/invoice
sudo systemctl stop invoice-service  # 如果已部署
# 重新部署或重启服务
sudo bash deploy.sh
```

## 📝 注意事项

1. **首次上传**：建议使用完整部署脚本 `deploy.sh`
2. **更新代码**：可以只上传修改的文件，然后重启服务
3. **备份**：重要更新前建议备份服务器上的代码
4. **测试**：上传后先在测试环境验证，再部署到生产环境

## 🔗 相关文档

- [部署指南](DEPLOY.md) - 详细的部署说明
- [README.md](README.md) - 项目说明









