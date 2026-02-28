@echo off
echo ====================================
echo 下载哈工大中文 BERT 模型
echo ====================================
echo.

cd /d %~dp0models\sentiment-analysis

echo 步骤 1: 检查 Git LFS...
git lfs --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] Git LFS 未安装，请先安装 Git LFS
    echo 下载地址：https://git-lfs.com
    echo.
    echo 按任意键退出...
    pause >nul
    exit /b 1
)
echo [成功] Git LFS 已安装
echo.

echo 步骤 2: 初始化 Git LFS...
git lfs install
echo.

echo 步骤 3: 克隆模型仓库（约 400MB，需要几分钟）...
echo 模型来源：https://huggingface.co/hfl/chinese-bert-wwm-ext
echo.

REM 如果已存在则先删除
if exist "chinese-bert-wwm-ext" (
    echo [提示] 发现已下载的模型，将重新下载
    rmdir /s /q chinese-bert-wwm-ext
)

git clone https://huggingface.co/hfl/chinese-bert-wwm-ext
if %errorlevel% neq 0 (
    echo [错误] 下载失败，请检查网络连接
    echo.
    echo 备选方案：手动下载
    echo 1. 访问：https://huggingface.co/hfl/chinese-bert-wwm-ext
    echo 2. 下载文件：pytorch_model.bin, config.json, vocab.txt
    echo 3. 放到当前目录
    echo.
    pause
    exit /b 1
)

echo.
echo [成功] 模型下载完成
echo.

echo 步骤 4: 移动模型文件...
cd chinese-bert-wwm-ext
for /f "delims=" %%i in ('dir /b') do (
    move "%%i" ..\ >nul 2>&1
)
cd ..
rmdir /s /q chinese-bert-wwm-ext

echo.
echo 步骤 5: 验证文件...
dir *.bin *.json *.txt

echo.
echo ====================================
echo 模型下载完成！
echo ====================================
echo.
echo 模型位置：%cd%
echo.
echo 下一步：
echo 1. 检查文件是否完整（应该有 pytorch_model.bin, config.json, vocab.txt）
echo 2. 运行启动脚本：start-sentiment-service.bat
echo.
pause
