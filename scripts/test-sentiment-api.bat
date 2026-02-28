@echo off
echo ====================================
echo 测试情感分析 API
echo ====================================
echo.

set BASE_URL=http://localhost:8080

echo 步骤 1: 检查服务状态...
curl -s %BASE_URL%/api/models/sentiment/health >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 服务未启动！
    echo.
    echo 请先运行：scripts\start-sentiment-service.bat
    echo.
    pause
    exit /b 1
)
echo [成功] 服务正在运行
echo.

echo ====================================
echo 测试 1: 健康检查
echo ====================================
curl -X GET %BASE_URL%/api/models/sentiment/health
echo.
echo.

echo ====================================
echo 测试 2: 单条文本分析 - 正面
echo ====================================
echo 文本：公司业绩大幅增长，净利润创新高
echo.
curl -X POST %BASE_URL%/api/models/sentiment/analyze ^
  -H "Content-Type: application/json" ^
  -d "{\"text\": \"公司业绩大幅增长，净利润创新高\"}"
echo.
echo.

echo ====================================
echo 测试 3: 单条文本分析 - 负面
echo ====================================
echo 文本：股票价格暴跌，公司亏损严重
echo.
curl -X POST %BASE_URL%/api/models/sentiment/analyze ^
  -H "Content-Type: application/json" ^
  -d "{\"text\": \"股票价格暴跌，公司亏损严重\"}"
echo.
echo.

echo ====================================
echo 测试 4: 单条文本分析 - 中性
echo ====================================
echo 文本：市场表现平稳，成交量一般
echo.
curl -X POST %BASE_URL%/api/models/sentiment/analyze ^
  -H "Content-Type: application/json" ^
  -d "{\"text\": \"市场表现平稳，成交量一般\"}"
echo.
echo.

echo ====================================
echo 测试 5: 批量分析
echo ====================================
curl -X POST %BASE_URL%/api/models/sentiment/analyze/batch ^
  -H "Content-Type: application/json" ^
  -d "{\"texts\": [\"公司业绩大幅增长\", \"股票价格暴跌\", \"市场表现平稳\"]}"
echo.
echo.

echo ====================================
echo 测试完成！
echo ====================================
echo.
pause
