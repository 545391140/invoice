# Install OpenJDK 17.0.15
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installing OpenJDK Platform 17.0.15" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check current Java version
Write-Host "[1] Checking current Java installation..." -ForegroundColor Yellow
try {
    $currentJava = java -version 2>&1 | Select-String "version"
    if ($currentJava) {
        Write-Host "Current Java version:" -ForegroundColor Gray
        java -version
        Write-Host ""
    }
} catch {
    Write-Host "No Java installation found" -ForegroundColor Gray
}

Write-Host "[2] Downloading OpenJDK 17.0.15..." -ForegroundColor Yellow

# Eclipse Adoptium (Temurin) download URL for 17.0.15
# Note: We'll use the API to get the exact download URL
$jdkVersion = "17.0.15"
$jdkBuild = "8"
$os = "windows"
$arch = "x64"
$packageType = "jdk"
$jdkImpl = "hotspot"

# Try to download from Eclipse Adoptium API
$apiUrl = "https://api.adoptium.net/v3/binary/version/jdk-$jdkVersion+$jdkBuild/$os/$arch/$packageType/$jdkImpl/normal/eclipse"
$downloadPath = "$env:USERPROFILE\Downloads"
$installerPath = "$downloadPath\OpenJDK17U-jdk_x64_windows_hotspot_${jdkVersion}_${jdkBuild}.msi"

Write-Host "Download URL: $apiUrl" -ForegroundColor Gray
Write-Host "Saving to: $installerPath" -ForegroundColor Gray
Write-Host ""

try {
    # Download the installer
    Write-Host "Downloading installer (this may take a few minutes)..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $apiUrl -OutFile $installerPath -UseBasicParsing
    
    if (Test-Path $installerPath) {
        Write-Host "[OK] Download complete!" -ForegroundColor Green
        Write-Host "File size: $([math]::Round((Get-Item $installerPath).Length / 1MB, 2)) MB" -ForegroundColor Gray
        Write-Host ""
        
        Write-Host "[3] Installing OpenJDK 17.0.15..." -ForegroundColor Yellow
        Write-Host "The installer will open. Please follow these steps:" -ForegroundColor Cyan
        Write-Host "  1. Click 'Next' through the installation wizard" -ForegroundColor Gray
        Write-Host "  2. Choose installation directory (default is recommended)" -ForegroundColor Gray
        Write-Host "  3. Make sure 'Add to PATH' is checked" -ForegroundColor Gray
        Write-Host "  4. Complete the installation" -ForegroundColor Gray
        Write-Host ""
        
        # Start the installer
        Start-Process msiexec.exe -ArgumentList "/i `"$installerPath`" /quiet /norestart" -Wait
        
        Write-Host ""
        Write-Host "[4] Configuring environment variables..." -ForegroundColor Yellow
        
        # Find the installed JDK
        $possiblePaths = @(
            "C:\Program Files\Eclipse Adoptium\jdk-$jdkVersion*",
            "C:\Program Files\Java\jdk-$jdkVersion*",
            "C:\Program Files\Eclipse Adoptium\jdk-17*"
        )
        
        $jdkPath = $null
        foreach ($pathPattern in $possiblePaths) {
            $found = Get-ChildItem -Path (Split-Path $pathPattern) -Directory -ErrorAction SilentlyContinue | 
                     Where-Object { $_.Name -like (Split-Path $pathPattern -Leaf) -and 
                                   (Test-Path "$($_.FullName)\bin\java.exe") } |
                     Sort-Object Name -Descending | Select-Object -First 1
            
            if ($found) {
                $jdkPath = $found.FullName
                break
            }
        }
        
        if ($jdkPath) {
            Write-Host "[OK] Found JDK at: $jdkPath" -ForegroundColor Green
            
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
            Write-Host "Installation Complete!" -ForegroundColor Green
            Write-Host "========================================" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "[IMPORTANT] Please restart your terminal for changes to take effect." -ForegroundColor Yellow
            Write-Host ""
            Write-Host "After restarting, verify with:" -ForegroundColor Cyan
            Write-Host "  java -version" -ForegroundColor Gray
            Write-Host ""
            Write-Host "Expected output:" -ForegroundColor Cyan
            Write-Host "  openjdk version `"17.0.15`"" -ForegroundColor Gray
            
        } else {
            Write-Host "[WARNING] Could not find installed JDK automatically" -ForegroundColor Yellow
            Write-Host "Please manually set JAVA_HOME to the installation directory" -ForegroundColor Yellow
        }
        
    } else {
        Write-Host "[ERROR] Download failed" -ForegroundColor Red
    }
    
} catch {
    Write-Host "[ERROR] Failed to download/install: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Alternative: Please download manually from:" -ForegroundColor Yellow
    Write-Host "  https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Look for version 17.0.15" -ForegroundColor Gray
}

Write-Host ""

