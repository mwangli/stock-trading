Register-ArgumentCompleter -Native -CommandName @("stock", "stock.exe") -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)

    $commands = @(
        "data-collect", "data-generate",
        "model-prepare", "model-train", "model-rank", "model-evaluate",
        "trade-buy", "trade-sell",
        "broker-check", "broker-sync",
        "system-pull", "system-push", "system-clean",
        "-h", "--help"
    )
    $options = @{
        "data-collect" = @("--date", "--codes", "--type", "-h", "--help")
        "data-generate" = @("--date", "-h", "--help")
        "model-prepare" = @("--date", "--force", "-h", "--help")
        "model-train" = @("--force", "-h", "--help")
        "model-rank" = @("--date", "-h", "--help")
        "model-evaluate" = @("--date", "-h", "--help")
        "trade-buy" = @("-h", "--help")
        "trade-sell" = @("-h", "--help")
        "broker-check" = @("--force", "--resume", "-h", "--help")
        "broker-sync" = @("--date", "-h", "--help")
        "system-pull" = @("--scope", "-h", "--help")
        "system-push" = @("--scope", "--force", "-h", "--help")
        "system-clean" = @("--date", "-h", "--help")
    }
    $values = @{
        "--type" = @("stock", "daily", "minute5", "minute1")
        "--scope" = @("market", "prepared", "model", "state")
    }

    $tokens = @($commandAst.CommandElements | ForEach-Object { $_.Extent.Text })
    $current = if ($wordToComplete) { $wordToComplete } else { "" }
    $command = $tokens | Where-Object { $options.ContainsKey($_) } | Select-Object -Last 1
    $previous = if ($tokens.Count -gt 1) { $tokens[$tokens.Count - 2] } else { "" }
    $candidates = if ($values.ContainsKey($previous)) {
        $values[$previous]
    } elseif ($command) {
        $options[$command]
    } else {
        $commands
    }

    $candidates |
        Where-Object { $_ -like "$current*" } |
        ForEach-Object {
            [System.Management.Automation.CompletionResult]::new(
                $_, $_, "ParameterName", $_
            )
        }
}

if (Get-Command Set-PSReadLineKeyHandler -ErrorAction SilentlyContinue) {
    Set-PSReadLineKeyHandler -Key Tab -Function MenuComplete
}
