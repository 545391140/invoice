# Setup Microsoft Build of OpenJDK 17.0.6
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Setting up Microsoft OpenJDK 17.0.6" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Common installation paths for Microsoft OpenJDK
$possiblePaths = @(
    "C:\Program Files\Microsoft\jdk-17.0.6",
    "C:\Program Files\Microsoft\jdk-17.0.6-hotspot",
    "C:\Program Files\Microsoft\openjdk-17.0.6",
    "C:\Program Files\Microsoft\jdk-17.0.6+8",
    "C:\Program Files\Microsoft\jdk-17.0.6.10-hotspot",
    "C:\Program Files\Java\jdk-17.0.6",
    "C:\Program Files\Java\jdk-17.0.6-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.6",
    "$env:LOCALAPPDATA\Programs\Microsoft\jdk-17.0.6"
)

Write-Host "[1] Searching for Microsoft OpenJDK 17.0.6..." -ForegroundColor Yellow

$jdkPath = $null

# Check each possible path
foreach ($path in $possiblePaths) {
    if (Test-Path "$path\bin\java.exe") {
        Write-Host "Found Java at: $path" -ForegroundColor Gray
        
        # Verify it's Microsoft OpenJDK
        $versionOutput = & "$path\bin\java.exe" -version 2>&1 | Out-String
        
        if ($versionOutput -match "Microsoft") {
            $jdkPath = $path
            Write-Host "[OK] Found Microsoft OpenJDK!" -ForegroundColor Green
            Write-Host "Path: $jdkPath" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "Version info:" -ForegroundColor Gray
            & "$jdkPath\bin\java.exe" -version
            break
        }
    }
}

# If not found, search recursively in Program Files
if (-not $jdkPath) {
    Write-Host "Searching recursively in Program Files..." -ForegroundColor Yellow
    
    $searchDirs = @(
        "C:\Program Files\Microsoft",
        "C:\Program Files\Java"
    )
    
    foreach ($searchDir in $searchDirs) {
        if (Test-Path $searchDir) {
            $found = Get-ChildItem $searchDir -Directory -Recurse -ErrorAction SilentlyContinue | 
                     Where-Object { 
                         (Test-Path "$($_.FullName)\bin\java.exe") -and
                         ($_.Name -match "jdk|openjdk|17")
                     } |
                     ForEach-Object {
                         $javaExe = "$($_.FullName)\bin\java.exe"
                         $version = & $javaExe -version 2>&1 | Out-String
                         if ($version -match "Microsoft" -and $version -match "17\.0") {
                             return $_.FullName
                         }
                     } | Select-Object -First 1
            
            if ($found) {
                $jdkPath = $found
                Write-Host "[OK] Found at: $jdkPath" -ForegroundColor Green
                break
            }
        }
    }
}

if (-not $jdkPath) {
    Write-Host ""
    Write-Host "[WARNING] Microsoft OpenJDK 17.0.6 not found automatically" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Please provide the installation path:" -ForegroundColor Cyan
    Write-Host "Example: C:\Program Files\Microsoft\jdk-17.0.6-hotspot" -ForegroundColor Gray
    Write-Host ""
    
    # Try to get path from user (non-interactive fallback)
    $manualPath = $args[0]
    
    if ($manualPath -and (Test-Path "$manualPath\bin\java.exe")) {
        $jdkPath = $manualPath
        Write-Host "[OK] Using provided path: $jdkPath" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Please run this script with the installation path:" -ForegroundColor Red
        Write-Host "  .\setup-ms-openjdk.ps1 `"C:\Program Files\Microsoft\jdk-17.0.6-hotspot`"" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Or set it manually:" -ForegroundColor Yellow
        Write-Host "  [Environment]::SetEnvironmentVariable('JAVA_HOME', 'YOUR_PATH', 'User')" -ForegroundColor Gray
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
    # Remove old Java paths if any
    $pathArray = $currentPath -split ';' | Where-Object { 
        $_ -notmatch "java|jdk|openjdk" -or $_ -eq $javaBin 
    }
    $newPath = ($pathArray + $javaBin) -join ';'
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


