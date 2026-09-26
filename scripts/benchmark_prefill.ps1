param(
    [string]$DeviceIp = "192.168.1.100",
    [int]$Port = 8080,
    [string]$ApiKey = ""
)
# Benchmark TTFT (prefill) e velocità di decode via HTTP, in streaming.
# Stessa misura del pulsante [ BENCHMARK ] nel tab TEST dell'app.

Add-Type -AssemblyName System.Net.Http
$url = "http://$DeviceIp`:$Port/v1/chat/completions"
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
if ($ApiKey) { $client.DefaultRequestHeaders.Add("Authorization", "Bearer $ApiKey") }

function New-Document([int]$tokens) {
    $sb = New-Object System.Text.StringBuilder
    $i = 1
    while ($sb.Length -lt $tokens * 4) {
        [void]$sb.Append("Paragraph ${i}: warehouse $($i*7%13) shipped $($i*37%500) parcels to district $($i*11%29) ")
        [void]$sb.Append("on day $($i%30+1), with $($i*3%17) late deliveries caused by weather or traffic.`n")
        $i++
    }
    $sb.ToString()
}

function Invoke-Stream($messages, [int]$maxTokens) {
    $body = @{ model = "edge"; messages = $messages; stream = $true; temperature = 0; max_tokens = $maxTokens
               stream_options = @{ include_usage = $true } } | ConvertTo-Json -Depth 6
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $url)
    $req.Content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $resp = $client.SendAsync($req, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
    if (-not $resp.IsSuccessStatusCode) { throw "HTTP $([int]$resp.StatusCode): $($resp.Content.ReadAsStringAsync().Result)" }
    $reader = New-Object System.IO.StreamReader($resp.Content.ReadAsStreamAsync().Result)
    $ttft = $null; $text = ""; $usage = $null
    while (-not $reader.EndOfStream) {
        $line = $reader.ReadLine()
        if (-not $line.StartsWith("data:") -or $line -eq "data: [DONE]") { continue }
        $chunk = $line.Substring(5) | ConvertFrom-Json
        if ($chunk.error) { throw $chunk.error.message }
        foreach ($c in $chunk.choices) {
            if ($c.delta.content) {
                if ($null -eq $ttft) { $ttft = $sw.ElapsedMilliseconds }
                $text += $c.delta.content
            }
        }
        if ($chunk.usage) { $usage = $chunk.usage }
    }
    $total = $sw.ElapsedMilliseconds
    if ($null -eq $ttft) { $ttft = $total }
    $completion = if ($usage) { $usage.completion_tokens } else { 0 }
    $tps = if ($completion -gt 1 -and $total -gt $ttft) { ($completion - 1) * 1000.0 / ($total - $ttft) } else { 0 }
    $cached = if ($usage -and $usage.prompt_tokens_details) { $usage.prompt_tokens_details.cached_tokens } else { 0 }
    [pscustomobject]@{ Text = $text; Prompt = $usage.prompt_tokens; Cached = $cached; Ttft = $ttft; Tps = [Math]::Round($tps, 1) }
}

Write-Host "Target: $url`n"
Write-Host "| Prompt | Prompt tok | Cached tok | TTFT ms | Decode tok/s |`n|---|---|---|---|---|"
$last = $null
foreach ($target in @(500, 2000, 4000)) {
    $msgs = @(@{ role = "user"; content = (New-Document $target) + "`nIn one sentence, what is this document about?" })
    try {
        $r = Invoke-Stream $msgs 48
        Write-Host "| ~$target cold | $($r.Prompt) | $($r.Cached) | $($r.Ttft) | $($r.Tps) |"
        $last = @{ Target = $target; Messages = $msgs; Text = $r.Text }
    } catch {
        Write-Host "| ~$target cold | FAIL: $_ |"
    }
}
if ($last) {
    $msgs = $last.Messages + @(
        @{ role = "assistant"; content = $last.Text },
        @{ role = "user"; content = "How many parcels did paragraph 2 mention? Answer with the number only." })
    $r = Invoke-Stream $msgs 16
    Write-Host "| ~$($last.Target) + follow-up (warm) | $($r.Prompt) | $($r.Cached) | $($r.Ttft) | $($r.Tps) |"
    Write-Host "`nFollow-up answer (expected 74): $($r.Text.Trim())"
}
Write-Host "`nRiporta la tabella in STATE.md (sezione Misure)." -ForegroundColor Cyan
