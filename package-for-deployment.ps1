# ============================================
# 打包部署文件脚本
# 用于离线部署：将项目打包成压缩包，然后手动传输到服务器
# ============================================

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "打包部署文件" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否在项目根目录
if (-not (Test-Path "pom.xml")) {
    Write-Host "错误: 请在项目根目录运行此脚本" -ForegroundColor Red
    exit 1
}

# 配置
$packageName = "invoice-service-deploy"
$version = "1.0.0"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = "deploy-package"
$packageFile = "$packageName-$version-$timestamp.zip"

Write-Host "配置信息:" -ForegroundColor Yellow
Write-Host "  包名: $packageFile" -ForegroundColor Gray
Write-Host "  输出目录: $outputDir" -ForegroundColor Gray
Write-Host ""

# 创建输出目录
if (Test-Path $outputDir) {
    Write-Host "清理旧的输出目录..." -ForegroundColor Yellow
    Remove-Item $outputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outputDir | Out-Null

Write-Host "复制项目文件..." -ForegroundColor Cyan

# 需要包含的文件和目录
$includeItems = @(
    "src",
    "pom.xml",
    "deploy.sh",
    "deploy-quick.sh",
    "README.md",
    "DEPLOY.md",
    "UPLOAD-GUIDE.md"
)

# 需要排除的文件和目录
$excludePatterns = @(
    "node_modules",
    "target",
    ".git",
    "logs",
    "uploads",
    "outputs",
    "temp",
    "*.log",
    ".idea",
    ".vscode",
    "__pycache__",
    "*.pyc",
    "deploy-package"
)

# 复制文件
foreach ($item in $includeItems) {
    if (Test-Path $item) {
        Write-Host "  复制: $item" -ForegroundColor Gray
        Copy-Item -Path $item -Destination $outputDir -Recurse -Force
    } else {
        Write-Host "  跳过（不存在）: $item" -ForegroundColor Yellow
    }
}

# 复制其他重要文件（如果存在）
$additionalFiles = @(
    "*.md",
    "*.sh",
    "*.yml",
    "*.yaml"
)

foreach ($pattern in $additionalFiles) {
    Get-ChildItem -Path . -Filter $pattern -File | ForEach-Object {
        $relativePath = $_.FullName.Replace((Get-Location).Path + "\", "")
        if ($relativePath -notmatch "deploy-package") {
            Write-Host "  复制: $relativePath" -ForegroundColor Gray
            Copy-Item -Path $_.FullName -Destination (Join-Path $outputDir $relativePath) -Force
        }
    }
}

# 创建部署说明文件
Write-Host "创建部署说明..." -ForegroundColor Cyan

$deployInstructions = @"
# 离线部署说明

## 文件说明

此压缩包包含 Invoice Service 的完整部署文件。

## 部署步骤

### 1. 解压文件

\`\`\`bash
# 将压缩包上传到服务器后，解压
unzip $packageFile -d /tmp/
cd /tmp/invoice-service-deploy-*
\`\`\`

### 2. 检查文件

\`\`\`bash
# 确认文件完整
ls -la
# 应该看到: src/, pom.xml, deploy.sh 等文件
\`\`\`

### 3. 运行部署脚本

\`\`\`bash
# 方式一：完整部署（推荐生产环境）
sudo bash deploy.sh

# 方式二：快速部署（开发/测试环境）
bash deploy-quick.sh
\`\`\`

### 4. 配置环境变量

部署脚本会提示输入 ARK_API_KEY，如果没有，可以稍后设置：

\`\`\`bash
export ARK_API_KEY="your_api_key_here"
\`\`\`

## 前置要求

服务器需要：
- Linux 系统（Ubuntu/Debian/CentOS/RHEL/Fedora）
- Root 权限（用于安装依赖）
- 网络连接（用于下载 Java、Maven 等依赖）

如果服务器无法联网，请参考 DEPLOY.md 中的"离线安装依赖"部分。

## 目录结构

\`\`\`
.
├── src/              # 源代码
├── pom.xml           # Maven 配置文件
├── deploy.sh         # 完整部署脚本
├── deploy-quick.sh   # 快速部署脚本
├── README.md         # 项目说明
└── DEPLOY.md         # 详细部署文档
\`\`\`

## 注意事项

1. 确保服务器有足够的磁盘空间（至少 500MB）
2. 确保有 root 权限运行部署脚本
3. 部署前建议备份服务器上的现有文件
4. 详细说明请查看 DEPLOY.md

## 故障排查

如果部署遇到问题：

1. 查看日志：\`tail -f logs/invoice-service.log\`
2. 检查 Java 版本：\`java -version\`（需要 Java 17+）
3. 检查 Maven：\`mvn -version\`
4. 查看系统日志：\`journalctl -u invoice-service\`

## 联系支持

如有问题，请查看：
- DEPLOY.md - 详细部署文档
- README.md - 项目说明
"@

$deployInstructions | Out-File -FilePath (Join-Path $outputDir "DEPLOY-INSTRUCTIONS.md") -Encoding UTF8

# 创建快速开始脚本
Write-Host "创建快速开始脚本..." -ForegroundColor Cyan

$quickStart = @"
#!/bin/bash
# 快速部署脚本 - 解压后直接运行

echo "========================================"
echo "Invoice Service 快速部署"
echo "========================================"
echo ""

# 检查是否为 root
if [ "\$EUID" -ne 0 ]; then 
    echo "请使用 root 权限运行: sudo bash quick-start.sh"
    exit 1
fi

# 运行部署脚本
if [ -f "deploy.sh" ]; then
    bash deploy.sh
else
    echo "错误: 未找到 deploy.sh"
    exit 1
fi
"@

$quickStart | Out-File -FilePath (Join-Path $outputDir "quick-start.sh") -Encoding UTF8

# 创建压缩包
Write-Host ""
Write-Host "创建压缩包..." -ForegroundColor Cyan

# 使用 PowerShell 压缩
Compress-Archive -Path "$outputDir\*" -DestinationPath $packageFile -Force

if (Test-Path $packageFile) {
    $fileSize = (Get-Item $packageFile).Length / 1MB
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "打包完成！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "文件信息:" -ForegroundColor Yellow
    Write-Host "  文件名: $packageFile" -ForegroundColor Gray
    Write-Host "  大小: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Gray
    Write-Host "  位置: $(Join-Path (Get-Location) $packageFile)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "下一步操作:" -ForegroundColor Yellow
    Write-Host "  1. 将压缩包传输到服务器（U盘、FTP、或其他方式）" -ForegroundColor Gray
    Write-Host "  2. 在服务器上解压: unzip $packageFile -d /tmp/" -ForegroundColor Gray
    Write-Host "  3. 进入目录: cd /tmp/invoice-service-deploy-*" -ForegroundColor Gray
    Write-Host "  4. 运行部署: sudo bash deploy.sh" -ForegroundColor Gray
    Write-Host ""
    Write-Host "详细说明请查看压缩包内的 DEPLOY-INSTRUCTIONS.md" -ForegroundColor Cyan
    Write-Host ""
    
    # 清理临时目录（可选）
    $cleanup = Read-Host "是否删除临时目录 $outputDir? (y/n)"
    if ($cleanup -eq "y" -or $cleanup -eq "Y") {
        Remove-Item $outputDir -Recurse -Force
        Write-Host "临时目录已删除" -ForegroundColor Green
    }
} else {
    Write-Host ""
    Write-Host "错误: 压缩包创建失败" -ForegroundColor Red
    exit 1
}









