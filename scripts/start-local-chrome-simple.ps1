# 本地 Chrome 直连启动脚本
# 用于开发环境，使用本地 Chrome 进行会话持久化测试

$ChromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$ProfileDir = "$env:USERPROFILE\chrome-sessions\stock"
$DebugPort = 9222

# 创建 Profile 目录
if (-not (Test-Path $ProfileDir)) {
    New-Item -ItemType Directory -Force -Path $ProfileDir | Out-Null
    Write-Host "[INFO] 创建 Profile 目录: $ProfileDir"
}

# 检查端口是否被占用
$conn = Get-NetTCPConnection -LocalPort $DebugPort -ErrorAction SilentlyContinue
if ($conn) {
    Write-Host "[INFO] 端口 $DebugPort 已被占用，尝试连接现有实例..."

    try {
        $response = Invoke-RestMethod "http://localhost:$DebugPort/json" -TimeoutSec 3
        if ($response) {
            Write-Host "[SUCCESS] 已连接到 Chrome: $($response[0].title)"
            Write-Host "[INFO] WebSocket: $($response[0].webSocketDebuggerUrl)"
            exit 0
        }
    } catch {
        Write-Host "[WARN] 无法连接，将尝试重启 Chrome"
    }
}

# 启动 Chrome
Write-Host "[INFO] 启动 Chrome..."
$args = @(
    "--remote-debugging-port=$DebugPort",
    "--user-data-dir=$ProfileDir",
    "--disable-blink-features=AutomationControlled"
)

Start-Process -FilePath $ChromePath -ArgumentList $args

Start-Sleep -Seconds 3

# 验证启动
try {
    $response = Invoke-RestMethod "http://localhost:$DebugPort/json" -TimeoutSec 5
    if ($response) {
        Write-Host "[SUCCESS] Chrome 已启动"
        Write-Host "[INFO] 标题: $($response[0].title)"
        Write-Host "[INFO] CDP 地址: http://localhost:$DebugPort"
        exit 0
    }
} catch {
    Write-Host "[ERROR] Chrome 启动失败"
    exit 1
}
