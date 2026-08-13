@echo off
chcp 65001 > nul
title 启动企智通

set PROJECT_ROOT=D:\Users\30776\IdeaProjects\springaichat
set BACKEND_PORT=8080
set FRONTEND_PORT=5173

cd /d "%PROJECT_ROOT%"

echo 清理旧进程...
:: 杀掉 Windows 本机的 Redis（防止抢端口）
taskkill /f /im redis-server.exe >nul 2>&1
:: 杀掉后端和前端进程
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%BACKEND_PORT%"') do taskkill /f /pid %%p >nul 2>&1
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%FRONTEND_PORT%"') do taskkill /f /pid %%p >nul 2>&1
echo 清理完毕

echo 启动 Docker Redis Stack...
:: 检查容器是否存在并启动
docker start redis-stack >nul 2>&1
if errorlevel 1 (
    echo Redis Stack 容器不存在，正在创建...
    docker run -d --name redis-stack -p 6379:6379 -p 8001:8001 redis/redis-stack:latest
)
timeout /t 3 /nobreak >nul

echo 启动后端...
start "Backend" cmd /k "cd /d "%PROJECT_ROOT%" && mvn spring-boot:run"
timeout /t 15 /nobreak >nul

echo 启动前端...
start "Frontend" cmd /k "cd /d "%PROJECT_ROOT%\frontend" && npm run dev"

echo.
echo 全部启动完成
echo 前端：http://localhost:%FRONTEND_PORT%
echo Redis UI：http://localhost:8001
echo.
pause