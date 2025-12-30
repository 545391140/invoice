# ============================================
# 本地构建与打包脚本
# 功能：本地构建前后端，打包成可直接部署的压缩包
# ============================================

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "开始本地构建与打包" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查环境
Write-Host "[1/4] 检查环境..." -ForegroundColor Yellow
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "错误: 未找到 Maven (mvn)，请先安装并添加到 PATH" -ForegroundColor Red
    exit 1
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Host "错误: 未找到 Node.js (npm)，请先安装并添加到 PATH" -ForegroundColor Red
    exit 1
}
Write-Host "✓ 环境检查通过" -ForegroundColor Green
Write-Host ""

# 2. 构建后端
Write-Host "[2/4] 构建后端 (Java)..." -ForegroundColor Yellow
mvn clean package -DskipTests
if (-not (Test-Path "target/invoice-service-1.0.0.jar")) {
    Write-Host "错误: 后端构建失败，未找到 JAR 文件" -ForegroundColor Red
    exit 1
}
Write-Host "✓ 后端构建完成" -ForegroundColor Green
Write-Host ""

# 3. 构建前端
Write-Host "[3/4] 构建前端 (React)..." -ForegroundColor Yellow
Push-Location frontend
npm install
npm run build
Pop-Location
if (-not (Test-Path "frontend/dist")) {
    Write-Host "错误: 前端构建失败，未找到 dist 目录" -ForegroundColor Red
    exit 1
}
Write-Host "✓ 前端构建完成" -ForegroundColor Green
Write-Host ""

# 4. 打包产物
Write-Host "[4/4] 打包产物..." -ForegroundColor Yellow
$packageName = "invoice-service-build-artifacts"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$zipFile = "$packageName-$timestamp.zip"
$outputDir = "deploy-tmp"

# 创建临时目录
if (Test-Path $outputDir) { Remove-Item $outputDir -Recurse -Force }
New-Item -ItemType Directory -Path $outputDir | Out-Null
New-Item -ItemType Directory -Path "$outputDir/target" | Out-Null
New-Item -ItemType Directory -Path "$outputDir/frontend" | Out-Null

# 复制产物
Copy-Item "target/invoice-service-1.0.0.jar" -Destination "$outputDir/target/"
Copy-Item "frontend/dist" -Destination "$outputDir/frontend/" -Recurse
Copy-Item "deploy.sh" -Destination "$outputDir/"
Copy-Item "*.md" -Destination "$outputDir/"

# 创建启动说明
$instructions = @"
# 快速启动说明 (已构建版)

此压缩包已包含本地构建好的产物，在服务器上可直接部署启动。

## 部署步骤

1. 上传此压缩包到服务器并解压
2. 运行部署脚本（跳过构建步骤）：
   sudo SKIP_BUILD=true bash deploy.sh

## 脚本参数说明
- SKIP_BUILD=true: 告诉脚本不要在服务器上运行 mvn 和 npm build，直接使用包内的产物。
"@
$instructions | Out-File -FilePath "$outputDir/QUICK-START-BUILD.md" -Encoding UTF8

# 压缩
Write-Host "正在创建压缩包: $zipFile ..." -ForegroundColor Gray
Compress-Archive -Path "$outputDir\*" -DestinationPath $zipFile -Force

# 清理
Remove-Item $outputDir -Recurse -Force

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "打包完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "文件名: $zipFile" -ForegroundColor Yellow
Write-Host "部署命令: sudo SKIP_BUILD=true bash deploy.sh" -ForegroundColor Cyan
Write-Host ""
