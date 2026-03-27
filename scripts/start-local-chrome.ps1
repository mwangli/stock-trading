# Chrome 会话持久化启动脚本
# 使用本地 Chrome 进行开发调试，会话数据保存在本地目录

param(
    [string]$ProfileDir = "$env:USERPROFILE\chrome-sessions\stock",
    [int]$DebugPort = 9222,
    [switch]$Headless = $false,
    [switch]$Stop = $false
)

$ChromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"

# 检查 Chrome 是否存在
if (-not (Test-Path $ChromePath)) {
    $ChromePath = "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe"
}

if (-not (Test-Path $ChromePath)) {
    Write-Host "[ERROR] Chrome 未安装" -ForegroundColor Red
    exit 1
}

# 停止模式
if ($Stop) {
    $proc = Get-Process -Name "chrome" -ErrorAction SilentlyContinue
    if ($proc) {
        Stop-Process -Name "chrome" -Force
        Write-Host "[INFO] Chrome 已停止" -ForegroundColor Green
    } else {
        Write-Host "[INFO] Chrome 未运行" -ForegroundColor Yellow
    }
    exit 0
}

# 创建 Profile 目录
if (-not (Test-Path $ProfileDir)) {
    New-Item -ItemType Directory -Force -Path $ProfileDir | Out-Null
    Write-Host "[INFO] 创建 Profile 目录: $ProfileDir" -ForegroundColor Cyan
}

# 检查端口是否已被占用
$portInUse = Get-NetTCPConnection -LocalPort $DebugPort -ErrorAction SilentlyContinue
if ($portInUse) {
    Write-Host "[INFO] Chrome 调试端口 $DebugPort 已被占用，尝试连接现有实例..." -ForegroundColor Yellow

    try {
        $response = Invoke-RestMethod "http://localhost:$DebugPort/json" -TimeoutSec 3
        if ($response) {
            Write-Host "[SUCCESS] 连接到已有 Chrome 实例: $($response[0].title)" -ForegroundColor Green
            Write-Host "[INFO] WebSocket URL: $($response[0].webSocketDebuggerUrl)" -ForegroundColor Cyan
            exit 0
        }
    } catch {
        Write-Host "[WARN] 无法连接到现有实例，将重启 Chrome" -ForegroundColor Yellow
    }
}

# 启动参数
$args = @(
    "--remote-debugging-port=$DebugPort",
    "--user-data-dir=$ProfileDir",
    "--disable-blink-features=AutomationControlled",
    "--no-sandbox",
    "--disable-dev-shm-usage"
)

if ($Headless) {
    $args += "--headless=new"
}

# 启动 Chrome
Write-Host "[INFO] 启动 Chrome..." -ForegroundColor Cyan
Write-Host "[INFO] Profile 目录: $ProfileDir" -ForegroundColor Cyan
Write-Host "[INFO] 调试端口: $DebugPort" -ForegroundColor Cyan

$process = Start-Process -FilePath $ChromePath -ArgumentList $args -PassThru

# 等待 Chrome 启动
Start-Sleep -Seconds 3

# 检查是否成功启动
if ($process.HasExited) {
    Write-Host "[ERROR] Chrome 启动失败，退出代码: $($process.ExitCode)" -ForegroundColor Red
    exit 1
}

Write-Host "[SUCCESS] Chrome 已启动 (PID: $($process.Id))" -ForegroundColor Green

# 获取调试信息
Start-Sleep -Seconds 2
try {
    $response = Invoke-RestMethod "http://localhost:$DebugPort/json" -TimeoutSec 5
    if ($response) {
        Write-Host "[INFO] Chrome 标题: $($response[0].title)" -ForegroundColor Cyan
        Write-Host "[INFO] WebSocket: $($response[0].webSocketDebuggerUrl)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "[WARN] 无法获取 Chrome 调试信息" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========== 使用说明 ==========" -ForegroundColor White
Write-Host "1. Chrome 已启动，可访问需要登录的网站" -ForegroundColor White
Write-Host "2. 完成登录后，Cookie 将保存在: $ProfileDir" -ForegroundColor White
Write-Host "3. 下次启动时使用相同目录，会话将自动恢复" -ForegroundColor White
Write-Host "4. 运行脚本加 -Stop 参数可停止 Chrome" -ForegroundColor White
Write-Host "================================" -ForegroundColor White
