@echo off
chcp 65001 >nul
echo ========================================
echo 打包部署文件
echo ========================================
echo.
echo 此脚本将打包项目文件，用于离线部署到服务器
echo.

REM 检查是否在项目根目录
if not exist "pom.xml" (
    echo 错误: 请在项目根目录运行此脚本
    pause
    exit /b 1
)

echo 正在运行打包脚本...
echo.

powershell -ExecutionPolicy Bypass -File "package-for-deployment.ps1"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 打包完成！
    echo ========================================
    echo.
    echo 下一步操作:
    echo   1. 将生成的 zip 文件传输到服务器
    echo   2. 在服务器上解压并运行部署脚本
    echo.
    echo 详细说明请查看: 快速部署指南.md
    echo.
) else (
    echo.
    echo 打包失败，请检查错误信息
    echo.
)

pause


