@echo off
echo Starting Invoice Service with Java...
echo.

REM Set API Key
set ARK_API_KEY=de7e4292-2a15-4f5c-ae7c-2553c555fea8

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

