@echo off
echo Starting Invoice Service...
echo.
echo Please make sure ARK_API_KEY environment variable is set!
echo.

set ARK_API_KEY=%ARK_API_KEY%
if "%ARK_API_KEY%"=="" (
    echo Using API Key from application-local.yml
    echo If you want to use environment variable instead:
    echo   set ARK_API_KEY=your_api_key_here
)

echo Building project...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Starting application...
java -jar target/invoice-service-1.0.0.jar

pause

