# Setup Maven from Downloads folder
Write-Host "Searching for Maven in Downloads folder..." -ForegroundColor Yellow

$downloadsPath = "$env:USERPROFILE\Downloads"
$mavenFound = $false

# Search for Maven directories
$mavenDirs = Get-ChildItem $downloadsPath -Directory -ErrorAction SilentlyContinue | 
             Where-Object { $_.Name -match "maven|apache" }

if ($mavenDirs) {
    Write-Host "Found potential Maven directories:" -ForegroundColor Green
    $index = 1
    $mavenDirs | ForEach-Object {
        $mvnPath = "$($_.FullName)\bin\mvn.cmd"
        if (Test-Path $mvnPath) {
            Write-Host "  [$index] $($_.FullName) [VALID]" -ForegroundColor Green
        } else {
            Write-Host "  [$index] $($_.FullName) [No mvn.cmd found]" -ForegroundColor Yellow
        }
        $index++
    }
    
    $selectedMaven = $mavenDirs | Where-Object { Test-Path "$($_.FullName)\bin\mvn.cmd" } | Select-Object -First 1
    
    if ($selectedMaven) {
        $mavenHome = $selectedMaven.FullName
        Write-Host ""
        Write-Host "Using: $mavenHome" -ForegroundColor Cyan
        
        # Configure Maven
        $mavenBin = "$mavenHome\bin"
        
        # Set MAVEN_HOME
        [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
        Write-Host "[OK] MAVEN_HOME set to: $mavenHome" -ForegroundColor Green
        
        # Add to PATH
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$mavenBin*") {
            $newPath = "$currentPath;$mavenBin"
            [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
            Write-Host "[OK] Added Maven to PATH" -ForegroundColor Green
            $mavenFound = $true
        } else {
            Write-Host "[INFO] Maven already in PATH" -ForegroundColor Gray
            $mavenFound = $true
        }
    }
}

# Search for Maven zip files
if (-not $mavenFound) {
    Write-Host ""
    Write-Host "Searching for Maven zip files..." -ForegroundColor Yellow
    $mavenZips = Get-ChildItem $downloadsPath -Filter "*.zip" -ErrorAction SilentlyContinue | 
                 Where-Object { $_.Name -match "maven|apache" }
    
    if ($mavenZips) {
        Write-Host "Found Maven zip files:" -ForegroundColor Green
        $mavenZips | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor Cyan
        }
        
        Write-Host ""
        Write-Host "Maven needs to be extracted first." -ForegroundColor Yellow
        Write-Host "Please extract the zip file and run this script again, or provide the path:" -ForegroundColor Yellow
        
        $manualPath = Read-Host "Enter Maven installation path (or press Enter to skip)"
        if ($manualPath -and (Test-Path "$manualPath\bin\mvn.cmd")) {
            $mavenHome = $manualPath
            [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
            $mavenBin = "$mavenHome\bin"
            $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
            if ($currentPath -notlike "*$mavenBin*") {
                [Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "User")
            }
            Write-Host "[OK] Maven configured" -ForegroundColor Green
            $mavenFound = $true
        }
    }
}

if (-not $mavenFound) {
    Write-Host ""
    Write-Host "[WARNING] Maven not found in Downloads folder" -ForegroundColor Yellow
    Write-Host "Please provide the Maven installation path:" -ForegroundColor Cyan
    $manualPath = Read-Host "Enter full path"
    
    if ($manualPath -and (Test-Path "$manualPath\bin\mvn.cmd")) {
        $mavenHome = $manualPath
        [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
        $mavenBin = "$mavenHome\bin"
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$mavenBin*") {
            [Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "User")
        }
        Write-Host "[OK] Maven configured: $mavenHome" -ForegroundColor Green
        $mavenFound = $true
    } else {
        Write-Host "[ERROR] Invalid Maven path or mvn.cmd not found" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($mavenFound) {
    Write-Host "Maven configuration complete!" -ForegroundColor Green
    Write-Host "MAVEN_HOME: $mavenHome" -ForegroundColor Green
    Write-Host ""
    Write-Host "[IMPORTANT] Please restart your terminal for changes to take effect." -ForegroundColor Yellow
} else {
    Write-Host "Maven configuration failed." -ForegroundColor Red
    Write-Host "Please ensure Maven is extracted and provide the correct path." -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan






