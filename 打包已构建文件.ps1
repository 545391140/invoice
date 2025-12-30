# ============================================
# 本地构建与打包脚本
# 功能：本地构建前后端，打包成可直接部署的压缩包
# ============================================

$ErrorActionPreference = "Stop"

# 1. 环境检查
Write-Host "Checking environment..."
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "Maven not found" }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw "NPM not found" }

# 2. 构建后端
Write-Host "Building backend..."
mvn clean package -DskipTests

# 3. 构建前端
Write-Host "Building frontend..."
Push-Location frontend
npm install
npm run build
Pop-Location

# 4. 打包
Write-Host "Packaging..."
$outputDir = "deploy-package"
if (Test-Path $outputDir) { Remove-Item $outputDir -Recurse -Force }
New-Item -ItemType Directory -Path $outputDir
New-Item -ItemType Directory -Path "$outputDir/target"
New-Item -ItemType Directory -Path "$outputDir/frontend"

Copy-Item "target/invoice-service-1.0.0.jar" -Destination "$outputDir/target/"
Copy-Item "frontend/dist" -Destination "$outputDir/frontend/" -Recurse
Copy-Item "deploy.sh" -Destination "$outputDir/"
Copy-Item "*.md" -Destination "$outputDir/"

# 创建说明文件
$readme = @"
# 部署说明 (Artifacts版)

此包包含本地构建好的前后端产物。

## 部署命令
sudo SKIP_BUILD=true bash deploy.sh
"@
$readme | Out-File -FilePath "$outputDir/DEPLOY-ARTIFACTS.md" -Encoding UTF8

$zipFile = "invoice-service-ready.zip"
Compress-Archive -Path "$outputDir\*" -DestinationPath $zipFile -Force
Remove-Item $outputDir -Recurse -Force

Write-Host "Done! File: $zipFile"
Write-Host "Upload to server and run: sudo SKIP_BUILD=true bash deploy.sh"




