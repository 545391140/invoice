# Build artifacts locally and package them
$ErrorActionPreference = "Stop"

# 1. Environment check
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "Maven not found" }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw "NPM not found" }

# 2. Build Backend
mvn clean package -DskipTests

# 3. Build Frontend
Push-Location frontend
npm install
npm run build
Pop-Location

# 4. Package
$outputDir = "deploy-tmp"
if (Test-Path $outputDir) { Remove-Item $outputDir -Recurse -Force }
New-Item -ItemType Directory -Path $outputDir
New-Item -ItemType Directory -Path "$outputDir/target"
New-Item -ItemType Directory -Path "$outputDir/frontend"

Copy-Item "target/invoice-service-1.0.0.jar" -Destination "$outputDir/target/"
Copy-Item "frontend/dist" -Destination "$outputDir/frontend/" -Recurse
Copy-Item "deploy.sh" -Destination "$outputDir/"
Copy-Item "*.md" -Destination "$outputDir/"

$zipFile = "invoice-artifacts.zip"
Compress-Archive -Path "$outputDir\*" -DestinationPath $zipFile -Force
Remove-Item $outputDir -Recurse -Force

Write-Host "Build and package complete: $zipFile"





