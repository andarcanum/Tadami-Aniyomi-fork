param(
  [Parameter(Mandatory = $true)]
  [string]$ApiKey,
  [string]$BaseUrl = "https://api.airforce",
  [string[]]$Models = @("deepseek-v3.2", "deepseek-v3.2-thinking", "glm-5-fast"),
  [int]$TimeoutSec = 180
)

$ErrorActionPreference = "Stop"

function Write-Section {
  param([string]$Title)
  Write-Host ""
  Write-Host "=== $Title ==="
}

function Convert-JsonValueToText {
  param($Value)

  if ($null -eq $Value) { return "" }
  if ($Value -is [string]) { return $Value.Trim() }

  if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($item in $Value) {
      $parts.Add((Convert-JsonValueToText -Value $item))
    }
    return ($parts -join "`n").Trim()
  }

  $props = $Value.PSObject.Properties
  if ($props.Count -gt 0) {
    foreach ($key in @("text", "content", "output_text", "reasoning_content", "reasoning", "arguments")) {
      $p = $props | Where-Object { $_.Name -eq $key } | Select-Object -First 1
      if ($null -ne $p) {
        $candidate = Convert-JsonValueToText -Value $p.Value
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
          return $candidate.Trim()
        }
      }
    }

    $functionProp = $props | Where-Object { $_.Name -eq "function" } | Select-Object -First 1
    if ($null -ne $functionProp) {
      $candidate = Convert-JsonValueToText -Value $functionProp.Value
      if (-not [string]::IsNullOrWhiteSpace($candidate)) {
        return $candidate.Trim()
      }
    }
  }

  return ""
}

function Invoke-AirforceChat {
  param(
    [string]$Model,
    [array]$Messages,
    [string]$PromptName
  )

  $payload = @{
    model = $Model
    messages = $Messages
    temperature = 0.2
    top_p = 0.95
    max_tokens = 1024
    stream = $false
  }

  $headers = @{
    Authorization = "Bearer $ApiKey"
    "Content-Type" = "application/json"
  }

  $jsonPayload = $payload | ConvertTo-Json -Depth 16
  $uri = "$BaseUrl/v1/chat/completions"

  $started = Get-Date
  try {
    $response = Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -Body $jsonPayload -TimeoutSec $TimeoutSec
    $elapsed = [int]((Get-Date) - $started).TotalMilliseconds
    $bodyRaw = $response.Content
    $body = $bodyRaw | ConvertFrom-Json -Depth 64

    $choice = $null
    if ($null -ne $body.choices -and $body.choices.Count -gt 0) {
      $choice = $body.choices[0]
    }

    $messageText = ""
    $choiceText = ""
    $outputText = ""
    $reasoningText = ""
    $toolCallsText = ""
    $finishReason = ""

    if ($null -ne $choice) {
      $finishReason = [string]$choice.finish_reason
      $messageText = Convert-JsonValueToText -Value $choice.message.content
      $choiceText = Convert-JsonValueToText -Value $choice.text
      $outputText = Convert-JsonValueToText -Value $choice.output_text
      $reasoningText = Convert-JsonValueToText -Value $choice.message.reasoning_content
      if ([string]::IsNullOrWhiteSpace($reasoningText)) {
        $reasoningText = Convert-JsonValueToText -Value $choice.message.reasoning
      }
      $toolCallsText = Convert-JsonValueToText -Value $choice.message.tool_calls
    }

    $usage = $body.usage
    $promptTokens = if ($null -ne $usage) { [string]$usage.prompt_tokens } else { "" }
    $completionTokens = if ($null -ne $usage) { [string]$usage.completion_tokens } else { "" }
    $totalTokens = if ($null -ne $usage) { [string]$usage.total_tokens } else { "" }

    return [PSCustomObject]@{
      success = $true
      model = $Model
      prompt = $PromptName
      elapsedMs = $elapsed
      httpStatus = [int]$response.StatusCode
      finishReason = $finishReason
      promptTokens = $promptTokens
      completionTokens = $completionTokens
      totalTokens = $totalTokens
      messageContentLen = $messageText.Length
      choiceTextLen = $choiceText.Length
      outputTextLen = $outputText.Length
      reasoningLen = $reasoningText.Length
      toolCallsLen = $toolCallsText.Length
      bodyRaw = $bodyRaw
    }
  } catch {
    $elapsed = [int]((Get-Date) - $started).TotalMilliseconds
    $status = ""
    $errorBody = ""
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $status = [string]([int]$_.Exception.Response.StatusCode)
      try {
        $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $sr.ReadToEnd()
      } catch {
      }
    }
    return [PSCustomObject]@{
      success = $false
      model = $Model
      prompt = $PromptName
      elapsedMs = $elapsed
      httpStatus = $status
      error = $_.Exception.Message
      errorBody = $errorBody
      bodyRaw = ""
    }
  }
}

function Test-ModelsEndpoint {
  $headers = @{
    Authorization = "Bearer $ApiKey"
    "Content-Type" = "application/json"
  }
  try {
    $resp = Invoke-WebRequest -Method Get -Uri "$BaseUrl/v1/models" -Headers $headers -TimeoutSec $TimeoutSec
    $payload = $resp.Content | ConvertFrom-Json -Depth 32
    $ids = @()
    if ($null -ne $payload.data) {
      foreach ($item in $payload.data) {
        if ($null -ne $item.id) {
          $ids += [string]$item.id
        }
      }
    }
    $ids = $ids | Sort-Object -Unique
    return [PSCustomObject]@{
      success = $true
      status = [int]$resp.StatusCode
      modelCount = $ids.Count
      hasDeepseek = [bool]($ids -contains "deepseek-v3.2")
      hasDeepseekThinking = [bool]($ids -contains "deepseek-v3.2-thinking")
      hasGlmFast = [bool]($ids -contains "glm-5-fast")
      ids = $ids
    }
  } catch {
    return [PSCustomObject]@{
      success = $false
      error = $_.Exception.Message
    }
  }
}

$reportDir = Join-Path (Get-Location) "build/reports/airforce-diagnostics"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Write-Section "Airforce Diagnostics"
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Models: $($Models -join ", ")"
Write-Host "ReportDir: $reportDir"

Write-Section "Step 1 - /v1/models probe"
$modelsProbe = Test-ModelsEndpoint
if ($modelsProbe.success) {
  Write-Host "OK status=$($modelsProbe.status) modelCount=$($modelsProbe.modelCount)"
  Write-Host "deepseek-v3.2 present=$($modelsProbe.hasDeepseek)"
  Write-Host "deepseek-v3.2-thinking present=$($modelsProbe.hasDeepseekThinking)"
  Write-Host "glm-5-fast present=$($modelsProbe.hasGlmFast)"
} else {
  Write-Host "FAILED /v1/models: $($modelsProbe.error)"
}

$promptPlain = @(
  @{ role = "user"; content = "Reply with exact text: OK" }
)

$promptXml = @(
  @{ role = "system"; content = "Translate text and keep exact XML tags <s i='...'>...</s>. Output only XML tags, no explanations." },
  @{ role = "user"; content = "TRANSLATE from English to Russian. INPUT BLOCK:`n<s i='0'>Hello world.</s>`n<s i='1'>How are you?</s>" }
)

$allResults = New-Object System.Collections.Generic.List[object]

foreach ($model in $Models) {
  Write-Section "Step 2 - model=$model prompt=plain"
  $r1 = Invoke-AirforceChat -Model $model -Messages $promptPlain -PromptName "plain"
  $allResults.Add($r1)
  if ($r1.success) {
    Write-Host "OK status=$($r1.httpStatus) elapsedMs=$($r1.elapsedMs) finish_reason=$($r1.finishReason)"
    Write-Host "usage prompt=$($r1.promptTokens) completion=$($r1.completionTokens) total=$($r1.totalTokens)"
    Write-Host "lens message=$($r1.messageContentLen) text=$($r1.choiceTextLen) output_text=$($r1.outputTextLen) reasoning=$($r1.reasoningLen) tool_calls=$($r1.toolCallsLen)"
  } else {
    Write-Host "FAILED status=$($r1.httpStatus) elapsedMs=$($r1.elapsedMs) error=$($r1.error)"
    if (-not [string]::IsNullOrWhiteSpace($r1.errorBody)) {
      Write-Host "errorBody: $($r1.errorBody.Substring(0, [Math]::Min(400, $r1.errorBody.Length)))"
    }
  }

  Write-Section "Step 3 - model=$model prompt=xml_translate"
  $r2 = Invoke-AirforceChat -Model $model -Messages $promptXml -PromptName "xml_translate"
  $allResults.Add($r2)
  if ($r2.success) {
    Write-Host "OK status=$($r2.httpStatus) elapsedMs=$($r2.elapsedMs) finish_reason=$($r2.finishReason)"
    Write-Host "usage prompt=$($r2.promptTokens) completion=$($r2.completionTokens) total=$($r2.totalTokens)"
    Write-Host "lens message=$($r2.messageContentLen) text=$($r2.choiceTextLen) output_text=$($r2.outputTextLen) reasoning=$($r2.reasoningLen) tool_calls=$($r2.toolCallsLen)"
  } else {
    Write-Host "FAILED status=$($r2.httpStatus) elapsedMs=$($r2.elapsedMs) error=$($r2.error)"
    if (-not [string]::IsNullOrWhiteSpace($r2.errorBody)) {
      Write-Host "errorBody: $($r2.errorBody.Substring(0, [Math]::Min(400, $r2.errorBody.Length)))"
    }
  }
}

$timestamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
$jsonPath = Join-Path $reportDir "diagnostics-$timestamp.json"
$txtPath = Join-Path $reportDir "diagnostics-$timestamp.txt"

$safeDump = @{
  generatedAt = (Get-Date).ToString("o")
  baseUrl = $BaseUrl
  models = $Models
  modelsProbe = $modelsProbe
  results = $allResults
}

$safeDump | ConvertTo-Json -Depth 32 | Set-Content -Path $jsonPath -Encoding utf8

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("Airforce Diagnostics Summary")
$lines.Add("generatedAt=$((Get-Date).ToString("o"))")
$lines.Add("baseUrl=$BaseUrl")
$lines.Add("models=$($Models -join ",")")
$lines.Add("")
foreach ($r in $allResults) {
  if ($r.success) {
    $lines.Add("OK model=$($r.model) prompt=$($r.prompt) status=$($r.httpStatus) elapsedMs=$($r.elapsedMs) finish_reason=$($r.finishReason) usage=$($r.promptTokens)/$($r.completionTokens)/$($r.totalTokens) lens=message:$($r.messageContentLen),text:$($r.choiceTextLen),output_text:$($r.outputTextLen),reasoning:$($r.reasoningLen),tool_calls:$($r.toolCallsLen)")
  } else {
    $lines.Add("FAILED model=$($r.model) prompt=$($r.prompt) status=$($r.httpStatus) elapsedMs=$($r.elapsedMs) error=$($r.error)")
  }
}
$lines | Set-Content -Path $txtPath -Encoding utf8

Write-Section "Done"
Write-Host "JSON report: $jsonPath"
Write-Host "Text summary: $txtPath"
