[CmdletBinding()]
param(
    [ValidateSet('payment_smoke','payment_concurrent','refund_smoke','payment_idempotency','webhook_duplicate')]
    [string]$TestType,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$k6Path = 'C:\Program Files\k6\k6.exe'
$prepare = Join-Path $PSScriptRoot 'prepare-queue-load-test.ps1'
$script = Join-Path $PSScriptRoot 'payment-reliability.js'
$count = if ($TestType -eq 'payment_concurrent') { 10 } else { 1 }
$label = switch ($TestType) {
    'payment_smoke' { '결제 스모크 테스트' }
    'payment_concurrent' { '동시 결제 승인 테스트' }
    'refund_smoke' { '환불 스모크 테스트' }
    'payment_idempotency' { '결제 확정 중복 요청 테스트' }
    'webhook_duplicate' { 'Toss 웹훅 중복 전달 테스트' }
}

& $prepare -UserCount $count -BaseUrl $BaseUrl
$testId = "$label - $((Get-Date).ToString('yyyyMMddHHmmss'))"
$env:BASE_URL = $BaseUrl
$env:PAYMENT_TEST_TYPE = $TestType
$env:K6_PROMETHEUS_RW_SERVER_URL = 'http://127.0.0.1:9090/api/v1/write'
$env:K6_PROMETHEUS_RW_TREND_STATS = 'p(95),p(99),avg,max'
$env:K6_PROMETHEUS_RW_PUSH_INTERVAL = '1s'
Write-Host "Grafana 테스트 실행: $testId" -ForegroundColor DarkGray
& $k6Path run -o experimental-prometheus-rw --tag "testid=$testId" --tag "test_type=$TestType" $script
if ($LASTEXITCODE -ne 0) { throw "$label 실패" }
Write-Host "$label 통과" -ForegroundColor Green
