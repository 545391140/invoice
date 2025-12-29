# Download and setup standard Apache Maven
Write-Host "Downloading Apache Maven 3.9.6..." -ForegroundColor Yellow

$mavenVersion = "3.9.6"
$mavenUrl = "https://dlcdn.apache.org/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$downloadPath = "$env:USERPROFILE\Downloads"
$mavenZip = "$downloadPath\apache-maven-$mavenVersion-bin.zip"
$extractPath = "$downloadPath"

try {
    # Download Maven
    Write-Host "Downloading from Apache..." -ForegroundColor Gray
    Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip -UseBasicParsing
    
    Write-Host "[OK] Download complete" -ForegroundColor Green
    
    # Extract
    Write-Host "Extracting..." -ForegroundColor Gray
    Expand-Archive -Path $mavenZip -DestinationPath $extractPath -Force
    
    # Find extracted directory
    $mavenDir = Get-ChildItem $extractPath -Directory | Where-Object { $_.Name -like "apache-maven-*" } | Select-Object -First 1
    
    if ($mavenDir) {
        $mavenHome = $mavenDir.FullName
        Write-Host "[OK] Maven extracted to: $mavenHome" -ForegroundColor Green
        
        # Configure environment variables
        [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenHome, "User")
        $mavenBin = "$mavenHome\bin"
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$mavenBin*") {
            [Environment]::SetEnvironmentVariable("Path", "$currentPath;$mavenBin", "User")
        }
        
        Write-Host "[OK] Maven configured!" -ForegroundColor Green
        Write-Host "MAVEN_HOME: $mavenHome" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Please restart your terminal and run: mvn -version" -ForegroundColor Yellow
        
        # Cleanup zip file
        Remove-Item $mavenZip -Force -ErrorAction SilentlyContinue
    }
} catch {
    Write-Host "[ERROR] Failed to download/setup Maven: $_" -ForegroundColor Red
    Write-Host "Please download manually from: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
}






