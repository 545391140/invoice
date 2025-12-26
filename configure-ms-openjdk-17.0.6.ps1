# Configure Microsoft Build of OpenJDK 17.0.6 LTS
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Configuring Microsoft Build of OpenJDK 17.0.6" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Search for Microsoft OpenJDK installation
Write-Host "[1] Searching for Microsoft OpenJDK 17.0.6..." -ForegroundColor Yellow

$searchPaths = @(
    "C:\Program Files\Microsoft",
    "C:\Program Files (x86)\Microsoft",
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium"
)

$jdkPath = $null

foreach ($basePath in $searchPaths) {
    if (Test-Path $basePath) {
        $jdkDirs = Get-ChildItem $basePath -Directory -Recurse -ErrorAction SilentlyContinue | 
                   Where-Object { 
                       $_.Name -match "jdk.*17|openjdk.*17" -and 
                       (Test-Path "$($_.FullName)\bin\java.exe") 
                   } |
                   Sort-Object FullName -Descending
        
        foreach ($jdk in $jdkDirs) {
            # Check version
            $javaExe = "$($jdk.FullName)\bin\java.exe"
            try {
                $versionOutput = & $javaExe -version 2>&1 | Out-String
                
                # Check for Microsoft Build and version 17.0.6
                if ($versionOutput -match "Microsoft" -and $versionOutput -match "17\.0\.6") {
                    $jdkPath = $jdk.FullName
                    Write-Host "[OK] Found Microsoft OpenJDK 17.0.6 at: $jdkPath" -ForegroundColor Green
                    Write-Host "Version info:" -ForegroundColor Gray
                    & $javaExe -version
                    break
                } elseif ($versionOutput -match "Microsoft" -and $versionOutput -match "17\.0") {
                    # Found Microsoft OpenJDK 17.x (might be 17.0.6)
                    $jdkPath = $jdk.FullName
                    Write-Host "[OK] Found Microsoft OpenJDK 17.x at: $jdkPath" -ForegroundColor Green
                    Write-Host "Version info:" -ForegroundColor Gray
                    & $javaExe -version
                    break
                }
            } catch {
                # Continue searching
            }
        }
        
        if ($jdkPath) { break }
    }
}

# Also check common Microsoft installation locations
if (-not $jdkPath) {
    $msPaths = @(
        "C:\Program Files\Microsoft\jdk-17.0.6",
        "C:\Program Files\Microsoft\jdk-17.0.6-hotspot",
        "C:\Program Files\Microsoft\openjdk-17.0.6"
    )
    
    foreach ($msPath in $msPaths) {
        if (Test-Path "$msPath\bin\java.exe") {
            $jdkPath = $msPath
            Write-Host "[OK] Found at: $jdkPath" -ForegroundColor Green
            break
        }
    }
}

if (-not $jdkPath) {
    Write-Host "[WARNING] Microsoft OpenJDK 17.0.6 not found automatically" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Please enter the installation path manually:" -ForegroundColor Cyan
    Write-Host "(e.g., C:\Program Files\Microsoft\jdk-17.0.6-hotspot)" -ForegroundColor Gray
    $manualPath = Read-Host "JDK Path"
    
    if ($manualPath -and (Test-Path "$manualPath\bin\java.exe")) {
        $jdkPath = $manualPath
        Write-Host "[OK] Using manual path: $jdkPath" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Invalid path or java.exe not found" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please verify the installation path and try again." -ForegroundColor Yellow
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
Write-Host "  openjdk version `"17.0.6`" 2024-XX-XX" -ForegroundColor Gray
Write-Host "  OpenJDK Runtime Environment Microsoft-17.0.6+XX" -ForegroundColor Gray
Write-Host ""


