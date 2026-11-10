Register-ArgumentCompleter -Native -CommandName @("stock", "stock.exe") -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)

    $actions = @(
        "collect", "prepare", "train", "rank", "review",
        "buy", "sell", "check", "order", "clean", "pull", "push"
    )
    $options = @{
        collect = @("--date", "--codes", "--type", "--model")
        prepare = @("--date", "--force", "--model")
        train = @("--force", "--model")
        rank = @("--date", "--model")
        review = @("--date", "--model")
        buy = @("--model")
        sell = @("--model")
        check = @()
        order = @("--date")
        clean = @("--date")
        pull = @("--scope")
        push = @("--scope", "--force")
    }
    $values = @{
        "--type" = @("stock", "daily", "minute5", "minute1")
        "--scope" = @("market", "prepared", "model", "state")
    }

    $tokens = @($commandAst.CommandElements | ForEach-Object { $_.Extent.Text })
    $current = if ($wordToComplete) { $wordToComplete } else { "" }
    $action = $null
    foreach ($token in $tokens) {
        if ($actions -contains $token) {
            $action = $token
            break
        }
    }
    $previous = if ($tokens.Count -gt 1) { $tokens[$tokens.Count - 2] } else { "" }
    $candidates = if ($values.ContainsKey($previous)) {
        $values[$previous]
    } elseif ($null -eq $action) {
        $actions
    } else {
        $options[$action]
    }

    if ($current.StartsWith("-") -and $null -ne $action) {
        $candidates = $options[$action]
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
