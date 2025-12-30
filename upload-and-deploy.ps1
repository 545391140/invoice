# ============================================
# AWS EC2 One-Click Deployment Script
# ============================================

$ErrorActionPreference = "Stop"

# Configuration
$SSH_USER = "ec2-user"
$SSH_IP = "54.238.122.205"
$PEM_KEY = "C:\Users\ruijie\Downloads\invoice.pem"
$REMOTE_PATH = "/home/ec2-user/invoice-deploy"
$ZIP_FILE = "invoice-service-ready.zip"

Write-Host "========================================"
Write-Host "Starting Deployment to AWS EC2"
Write-Host "========================================"

# 1. Local Build and Package
Write-Host "[1/3] Packaging locally..."
# Call the packaging script (assuming it's already fixed or using the build commands directly)
# To be safe, I'll run the build commands directly here
Push-Location frontend
npm install
npm run build
Pop-Location

mvn clean package -DskipTests

$outputDir = "deploy-package-tmp"
if (Test-Path $outputDir) { Remove-Item $outputDir -Recurse -Force }
New-Item -ItemType Directory -Path $outputDir
New-Item -ItemType Directory -Path "$outputDir/target"
New-Item -ItemType Directory -Path "$outputDir/frontend"

Copy-Item "target/invoice-service-1.0.0.jar" -Destination "$outputDir/target/"
Copy-Item "frontend/dist" -Destination "$outputDir/frontend/" -Recurse
Copy-Item "deploy.sh" -Destination "$outputDir/"
Copy-Item "*.md" -Destination "$outputDir/"

Compress-Archive -Path "$outputDir\*" -DestinationPath $ZIP_FILE -Force
Remove-Item $outputDir -Recurse -Force

# 2. Upload to server
Write-Host "[2/3] Uploading to $SSH_IP..."
# Use double quotes for the entire destination string to ensure proper expansion
$remote_dest = "${SSH_USER}@${SSH_IP}:${REMOTE_PATH}/"
ssh -i "$PEM_KEY" -o StrictHostKeyChecking=no "${SSH_USER}@${SSH_IP}" "mkdir -p $REMOTE_PATH"
scp -i "$PEM_KEY" -o StrictHostKeyChecking=no "$ZIP_FILE" "$remote_dest"

# 3. Remote Deployment
Write-Host "[3/3] Executing deployment on server..."
$remote_cmd = "cd $REMOTE_PATH && sudo yum install -y unzip && unzip -o $ZIP_FILE && sudo SKIP_BUILD=true bash deploy.sh"

ssh -i "$PEM_KEY" -o StrictHostKeyChecking=no "${SSH_USER}@${SSH_IP}" "$remote_cmd"

Write-Host "========================================"
Write-Host "Deployment completed successfully!"
Write-Host "Frontend: http://$SSH_IP"
Write-Host "Backend: http://$SSH_IP:8080/health"
Write-Host "========================================"
