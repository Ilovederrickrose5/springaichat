@echo off
chcp 65001 > nul
title 启动前后端服务

:: 切换到项目根目录
cd /d "D:\Users\30776\IdeaProjects\springaichat"

echo ======================================
echo 正在清理残留端口 8080(后端)、5173(前端)
echo ======================================
:: 终止8080端口进程
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r ":8080"') do (
    taskkill /f /pid %%p >nul 2>&1
    echo 已关闭8080端口进程 PID:%%p
)
:: 终止5173端口进程
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r ":5173"') do (
    taskkill /f /pid %%p >nul 2>&1
    echo 已关闭5173端口进程 PID:%%p
)

echo.
echo ======================================
echo 正在启动 后端 Spring Boot...
echo ======================================
start "Spring Boot Backend" cmd /k "mvn spring-boot:run"

timeout /t 1 /nobreak >nul

echo ======================================
echo 正在启动 前端 Vue...
echo ======================================
start "Vue Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo 两个服务已在新窗口中启动！
echo - 后端日志窗口标题：Spring Boot Backend
echo - 前端日志窗口标题：Vue Frontend
echo.
echo 按任意键关闭本窗口（不影响前后端服务运行）
pause > nul