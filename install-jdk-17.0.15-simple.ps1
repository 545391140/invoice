# Simple script to install OpenJDK 17.0.15
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installing OpenJDK Platform 17.0.15" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Option 1: Download from Eclipse Adoptium (Recommended)" -ForegroundColor Yellow
Write-Host "Opening download page..." -ForegroundColor Cyan
Start-Process "https://adoptium.net/temurin/releases/?version=17"

Write-Host ""
Write-Host "Option 2: Download from Microsoft Build of OpenJDK" -ForegroundColor Yellow
Start-Sleep -Seconds 2
Start-Process "https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-17"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Download Instructions" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. In the browser window that opened:" -ForegroundColor Yellow
Write-Host "   - Find OpenJDK 17.0.15" -ForegroundColor Gray
Write-Host "   - Download the Windows x64 installer (.msi)" -ForegroundColor Gray
Write-Host ""
Write-Host "2. After downloading, run the installer:" -ForegroundColor Yellow
Write-Host "   - Follow the installation wizard" -ForegroundColor Gray
Write-Host "   - Make sure 'Add to PATH' is checked" -ForegroundColor Gray
Write-Host ""
Write-Host "3. After installation, run this command to configure:" -ForegroundColor Yellow
Write-Host "   .\configure-jdk-17.0.15.ps1" -ForegroundColor Cyan
Write-Host ""


