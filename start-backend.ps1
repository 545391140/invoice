# Start Backend Service Script
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Invoice Backend Service" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set API Key
$env:ARK_API_KEY = "de7e4292-2a15-4f5c-ae7c-2553c555fea8"

# Configure Java and Maven paths
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.6.10-hotspot"
$env:MAVEN_HOME = "C:\Users\ruijie\Downloads\apache-maven-3.9.5"

# Update PATH (properly escape backslashes)
$javaBin = "$env:JAVA_HOME\bin"
$mavenBin = "$env:MAVEN_HOME\bin"
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = "$javaBin;$mavenBin;$currentPath"

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Java: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host "  Maven: $env:MAVEN_HOME" -ForegroundColor Gray
Write-Host "  API Key: Configured" -ForegroundColor Gray
Write-Host ""

# Verify Java and Maven
Write-Host "Verifying environment..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    Write-Host "  Java: OK" -ForegroundColor Green
} catch {
    Write-Host "  Java: ERROR - Not found in PATH" -ForegroundColor Red
    exit 1
}

try {
    $mvnVersion = mvn -version 2>&1 | Select-String "Apache Maven"
    Write-Host "  Maven: OK" -ForegroundColor Green
} catch {
    Write-Host "  Maven: ERROR - Not found in PATH" -ForegroundColor Red
    exit 1
}

Write-Host ""
$jarPath = "target\invoice-service-1.0.0.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "JAR file not found. Compiling project first..." -ForegroundColor Yellow
    Write-Host ""
    # Use mvn clean package to build the JAR
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed! Exiting." -ForegroundColor Red
        exit 1
    }
    Write-Host "Build successful!" -ForegroundColor Green
    Write-Host ""
}

Write-Host "Starting Spring Boot application..." -ForegroundColor Cyan
Write-Host "Backend will be available at: http://localhost:8080" -ForegroundColor Green
Write-Host ""
Write-Host "Press Ctrl+C to stop the service" -ForegroundColor Yellow
Write-Host ""

# Start the JAR directly (more reliable than mvn spring-boot:run)
java -jar $jarPath

