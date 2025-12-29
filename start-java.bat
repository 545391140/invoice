@echo off
echo Starting Invoice Service with Java...
echo.

REM Set API Key
set ARK_API_KEY=sk-awaO6Dt5bDW2OTiRJotWuZvtkaIwnYIwAzi0Bwx49MzlZgJz

REM Check if JAR exists
if not exist "target\invoice-service-1.0.0.jar" (
    echo JAR file not found. Compiling first...
    echo.
    call mvn clean package -DskipTests
    
    if %ERRORLEVEL% NEQ 0 (
        echo Build failed!
        pause
        exit /b 1
    )
)

echo.
echo Starting application with Java...
java -jar target\invoice-service-1.0.0.jar

pause






