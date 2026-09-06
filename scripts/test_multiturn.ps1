param(
    [string]$DeviceIp = "192.168.1.100",
    [int]$Port = 8080
)

$url = "http://$DeviceIp`:$Port/v1/chat/completions"

Write-Host "=== Test Conversazione Multi-Turn (Verifica Full-Context S1) ===" -ForegroundColor Cyan
Write-Host "Target URL: $url" -ForegroundColor Yellow

$payload = @{
    model = "gemma-4-E2B-it"
    messages = @(
        @{ role = "user"; content = "Il mio nome in codice è Aquila." },
        @{ role = "assistant"; content = "Ricevuto, ti chiamerò Aquila." },
        @{ role = "user"; content = "Qual era il mio nome in codice?" }
    )
    temperature = 0.2
    stream = $false
} | ConvertTo-Json -Depth 5

Write-Host "`nInvio richiesta multi-turn (3 turni)..."
try {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-RestMethod -Uri $url -Method Post -Body $payload -ContentType "application/json"
    $stopwatch.Stop()

    Write-Host "`nRisposta ricevuta in $($stopwatch.ElapsedMilliseconds) ms:" -ForegroundColor Green
    Write-Host "Modello: $($response.model)"
    Write-Host "Ruolo: $($response.choices[0].message.role)"
    Write-Host "Contenuto: $($response.choices[0].message.content)" -ForegroundColor White
    Write-Host "`nToken Usage:" -ForegroundColor Gray
    Write-Host "  Prompt tokens: $($response.usage.prompt_tokens)"
    Write-Host "  Completion tokens: $($response.usage.completion_tokens)"
    Write-Host "  Total tokens: $($response.usage.total_tokens)"
    
    if ($response.choices[0].message.content -match "Aquila") {
        Write-Host "`n[SUCCESS] Il modello ha ricordato il contesto del primo turno!" -ForegroundColor Green
    } else {
        Write-Host "`n[NOTE] Verifica la risposta del modello per assicurarti che il contesto sia stato colto." -ForegroundColor Yellow
    }
} catch {
    Write-Host "`n[ERROR] Chiamata fallita: $_" -ForegroundColor Red
}
