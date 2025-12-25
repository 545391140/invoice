# Configure OpenJDK 17.0.15 after installation
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Configuring OpenJDK 17.0.15" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Search for installed JDK 17.0.15
Write-Host "[1] Searching for OpenJDK 17.0.15..." -ForegroundColor Yellow

$searchPaths = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft",
    "C:\Program Files (x86)\Java"
)

$jdkPath = $null

foreach ($basePath in $searchPaths) {
    if (Test-Path $basePath) {
        $jdkDirs = Get-ChildItem $basePath -Directory -ErrorAction SilentlyContinue | 
                   Where-Object { 
                       $_.Name -match "jdk.*17" -and 
                       (Test-Path "$($_.FullName)\bin\java.exe") 
                   } |
                   Sort-Object Name -Descending
        
        foreach ($jdk in $jdkDirs) {
            # Check version
            $javaExe = "$($jdk.FullName)\bin\java.exe"
            $versionOutput = & $javaExe -version 2>&1 | Out-String
            
            if ($versionOutput -match "17\.0\.15") {
                $jdkPath = $jdk.FullName
                Write-Host "[OK] Found OpenJDK 17.0.15 at: $jdkPath" -ForegroundColor Green
                break
            }
        }
        
        if ($jdkPath) { break }
    }
}

if (-not $jdkPath) {
    Write-Host "[WARNING] OpenJDK 17.0.15 not found automatically" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Please enter the installation path manually:" -ForegroundColor Cyan
    Write-Host "(e.g., C:\Program Files\Eclipse Adoptium\jdk-17.0.15+8-hotspot)" -ForegroundColor Gray
    $manualPath = Read-Host "JDK Path"
    
    if ($manualPath -and (Test-Path "$manualPath\bin\java.exe")) {
        $jdkPath = $manualPath
    } else {
        Write-Host "[ERROR] Invalid path or java.exe not found" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "[2] Configuring environment variables..." -ForegroundColor Yellow

# Set JAVA_HOME
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "User")
Write-Host "[OK] JAVA_HOME set to: $jdkPath" -ForegroundColor Green

# Add to PATH
$javaBin = "$jdkPath\bin"
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")

if ($currentPath -notlike "*$javaBin*") {
    $newPath = "$currentPath;$javaBin"
    [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    Write-Host "[OK] Added Java to PATH" -ForegroundColor Green
} else {
    Write-Host "[INFO] Java already in PATH" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Configuration Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "[IMPORTANT] Please restart your terminal for changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "After restarting, verify with:" -ForegroundColor Cyan
Write-Host "  java -version" -ForegroundColor Gray
Write-Host ""
Write-Host "Expected output:" -ForegroundColor Cyan
Write-Host "  openjdk version `"17.0.15`"" -ForegroundColor Gray
Write-Host ""

