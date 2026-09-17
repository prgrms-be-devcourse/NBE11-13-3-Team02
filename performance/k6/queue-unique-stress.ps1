param(
    [int[]]$Stages = @(100, 500, 1000, 2000),
    [int]$StabilizationSeconds = 5,
    [switch]$CorrectnessOnly,
    [ValidateSet('smoke', 'capacity', 'integrity')]
    [string]$TestType = 'capacity',
    [string]$K6ScriptName = 'queue-500-users.js'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$prepareScript = Join-Path $PSScriptRoot 'prepare-queue-load-test.ps1'
$burstScript = Join-Path $PSScriptRoot $K6ScriptName
$queueResetScript = Join-Path $PSScriptRoot 'reset-payment-queue.ps1'
$resultDirectory = Join-Path $PSScriptRoot 'results'
$k6Path = 'C:\Program Files\k6\k6.exe'

if (-not (Test-Path -LiteralPath $k6Path)) {
    throw "k6를 찾을 수 없습니다: $k6Path"
}
if (-not (Test-Path -LiteralPath $burstScript)) {
    throw "k6 스크립트를 찾을 수 없습니다: $burstScript"
}

function Wait-ForBackend {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw '백엔드 시작을 90초 내에 확인하지 못했습니다.'
}

New-Item -ItemType Directory -Force $resultDirectory | Out-Null
Wait-ForBackend

foreach ($userCount in $Stages) {
    if ($userCount -lt 1) {
        throw "각 단계는 최소 1명이어야 합니다: $userCount"
    }

    Write-Host ''
    Write-Host "===== 고유 사용자 $userCount명 대기열 부하 테스트 =====" -ForegroundColor Cyan

    # 서버는 워밍업된 상태로 유지하고, 이전 단계의 결제 대기열 키만 제거합니다.
    & $queueResetScript

    & $prepareScript -UserCount $userCount

    # 대량 사용자 생성과 JWT 발급으로 생긴 일시적인 DB/JVM 부하를 분리합니다.
    # 이 대기 뒤의 k6 요청만 대기열 진입 동시성 측정값에 반영됩니다.
    Write-Host "준비 작업 안정화 대기: $StabilizationSeconds초..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $StabilizationSeconds
    Wait-ForBackend

    $testName = "queue-$TestType"
    $resultPath = Join-Path $resultDirectory ("$testName-$userCount.txt")
    $testMode = $TestType
    # Grafana 선택 목록에는 내부 식별자 대신 사람이 읽을 수 있는 테스트 이름을 표시합니다.
    $testDisplayName = switch ($testMode) {
        'smoke' { '스모크 테스트' }
        'integrity' { '대기열 정합성 테스트' }
        'capacity' { '시스템 수용량 테스트' }
    }
    # 예: 대기열 정합성 테스트 - 500명 - 20260917103000
    $testId = "$testDisplayName - ${userCount}명 - $((Get-Date).ToString('yyyyMMddHHmmss'))"
    Write-Host "Grafana k6 testid: $testId" -ForegroundColor DarkGray
    # k6는 요청 실패를 stderr 경고로 출력합니다. PowerShell 예외로 중단하지 않고
    # 전체 요약과 종료 코드를 받아 임계점 후보로 기록합니다.
    $previousErrorActionPreference = $ErrorActionPreference
    $previousQueueTestMode = $env:QUEUE_TEST_MODE
    $previousRemoteWriteUrl = $env:K6_PROMETHEUS_RW_SERVER_URL
    $previousTrendStats = $env:K6_PROMETHEUS_RW_TREND_STATS
    $previousPushInterval = $env:K6_PROMETHEUS_RW_PUSH_INTERVAL
    if ($CorrectnessOnly) {
        $env:QUEUE_TEST_MODE = 'correctness'
    } else {
        Remove-Item Env:QUEUE_TEST_MODE -ErrorAction SilentlyContinue
    }
    $env:K6_PROMETHEUS_RW_SERVER_URL = 'http://127.0.0.1:9090/api/v1/write'
    $env:K6_PROMETHEUS_RW_TREND_STATS = 'p(95),p(99),avg,max'
    $env:K6_PROMETHEUS_RW_PUSH_INTERVAL = '1s'
    $ErrorActionPreference = 'Continue'
    & $k6Path run -o experimental-prometheus-rw --tag "testid=$testId" --tag "test_type=$testMode" --tag "users=$userCount" $burstScript 2>&1 | Tee-Object -FilePath $resultPath
    $k6ExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($null -eq $previousQueueTestMode) {
        Remove-Item Env:QUEUE_TEST_MODE -ErrorAction SilentlyContinue
    } else {
        $env:QUEUE_TEST_MODE = $previousQueueTestMode
    }
    if ($null -eq $previousRemoteWriteUrl) { Remove-Item Env:K6_PROMETHEUS_RW_SERVER_URL -ErrorAction SilentlyContinue } else { $env:K6_PROMETHEUS_RW_SERVER_URL = $previousRemoteWriteUrl }
    if ($null -eq $previousTrendStats) { Remove-Item Env:K6_PROMETHEUS_RW_TREND_STATS -ErrorAction SilentlyContinue } else { $env:K6_PROMETHEUS_RW_TREND_STATS = $previousTrendStats }
    if ($null -eq $previousPushInterval) { Remove-Item Env:K6_PROMETHEUS_RW_PUSH_INTERVAL -ErrorAction SilentlyContinue } else { $env:K6_PROMETHEUS_RW_PUSH_INTERVAL = $previousPushInterval }

    if ($k6ExitCode -ne 0) {
        $criterion = switch ($testMode) {
            'capacity' { '시스템 수용량 기준(연결 거부 또는 서버 도달 실패)' }
            'integrity' { '대기열 정합성 기준' }
            'smoke' { '스모크 기준' }
            default { 'k6 성능/정합성 기준' }
        }
        Write-Host "임계점 후보: ${userCount}명 단계에서 $criterion 이 깨졌습니다. 결과: $resultPath" -ForegroundColor Yellow
        break
    }

    $retryMatch = Select-String -LiteralPath $resultPath -Pattern 'queue_connection_retries\.+:\s+(\d+)' | Select-Object -Last 1
    $retryCount = if ($retryMatch -and $retryMatch.Matches[0].Groups.Count -gt 1) { [int]$retryMatch.Matches[0].Groups[1].Value } else { 0 }
    Write-Host "정합성 통과(최종 결과): ${userCount}명 → admitted=10, waiting=$($userCount - 10)" -ForegroundColor Green
    if ($retryCount -gt 0) {
        Write-Host "주의: 첫 연결에서 $retryCount 건이 거부됐지만 재시도 후 모두 성공했습니다. '첫 요청 무오류'는 아닙니다." -ForegroundColor Yellow
    }
}
