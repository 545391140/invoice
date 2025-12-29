# Configure Java and Maven Environment Variables
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Configuring Java and Maven Environment" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to find Java installation
function Find-JavaInstallation {
    $paths = @(
        "C:\Program Files\Java",
        "C:\Program Files (x86)\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Microsoft",
        "$env:LOCALAPPDATA\Programs\Java",
        "$env:ProgramFiles\Java",
        "C:\Java",
        "D:\Java",
        "D:\Program Files\Java"
    )
    
    foreach ($basePath in $paths) {
        if (Test-Path $basePath) {
            $jdkDirs = Get-ChildItem $basePath -Directory -ErrorAction SilentlyContinue | 
                       Where-Object { $_.Name -match "jdk|java|jre" -and (Test-Path "$($_.FullName)\bin\java.exe") }
            
            if ($jdkDirs) {
                $jdk = $jdkDirs | Sort-Object Name -Descending | Select-Object -First 1
                return $jdk.FullName
            }
        }
    }
    
    # Check registry
    try {
        $regPath = Get-ItemProperty "HKLM:\SOFTWARE\JavaSoft\Java Development Kit\*" -ErrorAction SilentlyContinue
        if ($regPath) {
            $latest = $regPath | Sort-Object PSChildName -Descending | Select-Object -First 1
            if ($latest.JavaHome -and (Test-Path "$($latest.JavaHome)\bin\java.exe")) {
                return $latest.JavaHome
            }
        }
    } catch {}
    
    return $null
}

# Function to find Maven installation
function Find-MavenInstallation {
    $paths = @(
        "C:\Program Files\Apache",
        "C:\Program Files (x86)\Apache",
        "C:\apache-maven",
        "C:\maven",
        "D:\apache-maven",
        "D:\maven",
        "$env:LOCALAPPDATA\Programs\Apache"
    )
    
    foreach ($basePath in $paths) {
        if (Test-Path $basePath) {
            $mavenDirs = Get-ChildItem $basePath -Directory -ErrorAction SilentlyContinue | 
                         Where-Object { $_.Name -match "maven" -and (Test-Path "$($_.FullName)\bin\mvn.cmd") }
            
            if ($mavenDirs) {
                $maven = $mavenDirs | Sort-Object Name -Descending | Select-Object -First 1
                return $maven.FullName
            }
        }
    }
    
    return $null
}

# Find Java
Write-Host "[1] Searching for Java installation..." -ForegroundColor Yellow
$javaHome = Find-JavaInstallation

if ($javaHome) {
    Write-Host "[OK] Found Java at: $javaHome" -ForegroundColor Green
    $javaBin = "$javaHome\bin"
    
    if (Test-Path "$javaBin\java.exe") {
        Write-Host "[OK] Java executable found" -ForegroundColor Green
        
        # Set JAVA_HOME
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
        Write-Host "[OK] JAVA_HOME set to: $javaHome" -ForegroundColor Green
        
        # Add to PATH
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$javaBin*") {
            $newPath = "$currentPath;$javaBin"
            [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
            Write-Host "[OK] Added Java to PATH" -ForegroundColor Green
        } else {
            Write-Host "[INFO] Java already in PATH" -ForegroundColor Gray
        }
    } else {
        Write-Host "[ERROR] Java executable not found at: $javaBin\java.exe" -ForegroundColor Red
        $javaHome = $null
    }
} else {
    Write-Host "[WARNING] Java installation not found automatically" -ForegroundColor Yellow
    Write-Host "Please enter Java installation path (e.g., C:\Program Files\Java\jdk-17):" -ForegroundColor Cyan
    $manualJava = Read-Host
    if ($manualJava -and (Test-Path "$manualJava\bin\java.exe")) {
        $javaHome = $manualJava
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
        $javaBin = "$javaHome\bin"
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$javaBin*") {
            [Environment]::SetEnvironmentVariable("Path", "$currentPath;$javaBin", "User")
        }
        Write-Host "[OK] Java configured" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Invalid Java path" -ForegroundColor Red
    }
}

Write-Host ""

# Find Maven
Write-Host "[2] Searching for Maven installation..." -ForegroundColor Yellow
$mavenHome = Find-MavenInstallation

if ($mavenHome) {
    Write-Host "[OK] Found Maven at: $mavenHome" -ForegroundColor Green
    $mavenBin = "$mavenHome\bin"
    
    if (Test-Path "$mavenBin\mvn.cmd") {
        Write-Host "[OK] Maven executable found" -ForegroundColor Green
        
        # Set MAVEN_HOME
        [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
        Write-Host "[OK] MAVEN_HOME set to: $mavenHome" -ForegroundColor Green
        
        # Add to PATH
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$mavenBin*") {
            $newPath = "$currentPath;$mavenBin"
            [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
            Write-Host "[OK] Added Maven to PATH" -ForegroundColor Green
        } else {
            Write-Host "[INFO] Maven already in PATH" -ForegroundColor Gray
        }
    } else {
        Write-Host "[ERROR] Maven executable not found at: $mavenBin\mvn.cmd" -ForegroundColor Red
        $mavenHome = $null
    }
} else {
    Write-Host "[WARNING] Maven installation not found automatically" -ForegroundColor Yellow
    Write-Host "Please enter Maven installation path (e.g., C:\Program Files\Apache\apache-maven-3.9.6):" -ForegroundColor Cyan
    $manualMaven = Read-Host
    if ($manualMaven -and (Test-Path "$manualMaven\bin\mvn.cmd")) {
        $mavenHome = $manualMaven
        [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
        $mavenBin = "$mavenHome\bin"
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$mavenBin*") {
            [Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "User")
        }
        Write-Host "[OK] Maven configured" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] Invalid Maven path" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Configuration Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($javaHome) {
    Write-Host "JAVA_HOME: $javaHome" -ForegroundColor Green
} else {
    Write-Host "JAVA_HOME: Not configured" -ForegroundColor Red
}

if ($mavenHome) {
    Write-Host "MAVEN_HOME: $mavenHome" -ForegroundColor Green
} else {
    Write-Host "MAVEN_HOME: Not configured" -ForegroundColor Red
}

Write-Host ""
Write-Host "[IMPORTANT] Environment variables have been set for your user account." -ForegroundColor Yellow
Write-Host "Please restart your terminal/PowerShell window for changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "After restarting, verify with:" -ForegroundColor Cyan
Write-Host "  java -version" -ForegroundColor Gray
Write-Host "  mvn -version" -ForegroundColor Gray
Write-Host ""






