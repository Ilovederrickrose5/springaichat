@echo off
chcp 65001 > nul
title 启动前后端服务
//.\start-all.bat
:: 切换到项目根目录
cd /d "D:\Users\30776\IdeaProjects\springaichat"

:: Redis 安装路径（如路径不同请修改）
set REDIS_PATH=D:\Program Files\Redis\redis-server.exe

echo ======================================
echo 正在清理残留端口 6379(Redis)、8080(后端)、5173(前端)
echo ======================================
:: 终止6379端口进程
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r ":6379"') do (
    taskkill /f /pid %%p >nul 2>&1
    echo 已关闭6379端口进程 PID:%%p
)
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
echo 正在启动 Redis 服务...
echo ======================================
if exist "%REDIS_PATH%" (
    start "Redis Server" cmd /k ""%REDIS_PATH%""
    echo Redis 服务已启动
) else (
    echo [警告] 未找到 Redis: %REDIS_PATH%
    echo 请修改本脚本中的 REDIS_PATH 变量指向正确的 redis-server.exe 路径
    echo 或确保 Redis 已加入系统 PATH 环境变量
    where redis-server >nul 2>&1
    if %errorlevel%==0 (
        start "Redis Server" cmd /k "redis-server"
        echo 已通过 PATH 找到 Redis 并启动
    ) else (
        echo [错误] 无法启动 Redis，请先安装 Redis 或配置正确路径
    )
)

timeout /t 2 /nobreak >nul

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
echo 三个服务已在新窗口中启动！
echo - Redis 日志窗口标题：Redis Server
echo - 后端日志窗口标题：Spring Boot Backend
echo - 前端日志窗口标题：Vue Frontend
echo.
echo 按任意键关闭本窗口（不影响各服务运行）
pause > nul