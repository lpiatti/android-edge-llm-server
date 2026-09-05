param(
    [string]$DeviceIp = "192.168.1.100",
    [int]$Port = 8080
)

$url = "http://$DeviceIp`:$Port/v1/chat/completions"

Write-Host "=== Benchmark Prefill Time-To-First-Token (TTFT) Sessione S1 ===" -ForegroundColor Cyan
Write-Host "Dispositivo: $url`n" -ForegroundColor Yellow

$contextSizes = @(500, 2000, 4000)

# Blocco di testo ripetibile (~50 token, ~200 caratteri)
$chunk = "L'architettura edge computing su Android permette di distribuire nodi di inferenza neurale direttamente sulla rete locale, garantendo la totale privacy del dato e operativita continua senza connettivita internet. "

foreach ($targetTokens in $contextSizes) {
    # Stima caratteri necessari: ~4 caratteri per token
    $targetChars = $targetTokens * 4
    $repeats = [Math]::Max(1, [int]($targetChars / $chunk.Length))
    $syntheticText = ($chunk * $repeats).Substring(0, [Math]::Min($targetChars, ($chunk * $repeats).Length))
    
    $payload = @{
        model = "gemma-4-E2B-it"
        messages = @(
            @{ role = "user"; content = "Ecco il documento: $syntheticText`n`nRiassumi brevemente in una riga." }
        )
        stream = $false
    } | ConvertTo-Json -Depth 5

    Write-Host "Test contesto stimato ~ $targetTokens token (payload: $($syntheticText.Length) caratteri)..." -NoNewline

    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $res = Invoke-RestMethod -Uri $url -Method Post -Body $payload -ContentType "application/json"
        $sw.Stop()
        $elapsedMs = $sw.ElapsedMilliseconds
        $promptTokens = $res.usage.prompt_tokens
        $complTokens = $res.usage.completion_tokens

        Write-Host " OK!" -ForegroundColor Green
        Write-Host "  Tempo totale: $elapsedMs ms"
        Write-Host "  Prompt tokens effettivi: $promptTokens | Token completamento: $complTokens"
        Write-Host "  Velocita stimata prefill: $([Math]::Round($promptTokens / ($elapsedMs / 1000.0), 1)) token/s`n"
    } catch {
        Write-Host " FALLITO!" -ForegroundColor Red
        Write-Host "  Errore: $_`n"
    }
}

Write-Host "Benchmark completato. Riporta i valori nella PR o in docs/project-state.md." -ForegroundColor Cyan
