@echo off
echo ====================================
echo 启动情感分析服务
echo ====================================
echo.

cd /d %~dp0..\backend

echo 步骤 1: 检查配置...
echo 模型：hfl/chinese-bert-wwm-ext
echo 路径：models\sentiment-analysis
echo.

echo 步骤 2: 检查模型文件...
if not exist "..\models\sentiment-analysis\pytorch_model.bin" (
    echo [错误] 模型文件不存在！
    echo.
    echo 请先运行下载脚本:
    echo   scripts\download-bert-model.bat
    echo.
    pause
    exit /b 1
)
echo [成功] 模型文件存在
echo.

echo 步骤 3: 启动 Spring Boot 应用...
echo.

mvn spring-boot:run

echo.
echo ====================================
echo 服务已停止
echo ====================================
pause
