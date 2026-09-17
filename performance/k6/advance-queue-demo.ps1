param(
    [int]$BatchCount = 1,
    [ValidateRange(15, 300)]
    [int]$BatchIntervalSeconds = 20,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$dataPath = Join-Path $PSScriptRoot 'generated\queue-load-test.json'

if (-not (Test-Path -LiteralPath $dataPath)) {
    throw "테스트 데이터가 없습니다. .\\performance\\k6\\prepare-queue-load-test.ps1 를 먼저 실행하세요."
}

$testData = Get-Content -Raw -LiteralPath $dataPath | ConvertFrom-Json
$groupBuyId = $testData.groupBuyId
$startedAt = Get-Date

Write-Host "Grafana 확인 기준 시각: $($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor DarkGray
Write-Host "이 데모는 k6 테스트가 아닙니다. Grafana의 시간 범위를 이 시각 이후로 좁혀 대기열 상태·흐름 패널을 확인하세요." -ForegroundColor DarkGray

function Get-QueueSnapshot {
    param([object[]]$Users)

    $snapshot = @()

    foreach ($user in $Users) {
        $headers = @{ Authorization = "Bearer $($user.accessToken)" }
        # 이미 발급된 토큰이 있으면 동일 토큰과 현재 상태를 돌려줍니다.
        $queue = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/group-buys/$groupBuyId/queue-token" -Headers $headers
        $snapshot += [PSCustomObject]@{
            User = $user
            QueueToken = $queue.queueToken
            Status = $queue.status
        }
    }

    return $snapshot
}

$remainingUsers = @($testData.users)

for ($batch = 1; $batch -le $BatchCount; $batch++) {
    Write-Host "[$batch/$BatchCount] 현재 ADMITTED 사용자 10명을 찾는 중..."
    $snapshot = Get-QueueSnapshot -Users $remainingUsers
    $admitted = @($snapshot | Where-Object { $_.Status -eq 'ADMITTED' } | Select-Object -First 10)

    if ($admitted.Count -ne 10) {
        throw "현재 ADMITTED 사용자가 10명이 아닙니다. 실제 값: $($admitted.Count)"
    }

    Write-Host "  로컬 PG 승인 성공으로 10명의 결제를 완료합니다."
    foreach ($entry in $admitted) {
        $headers = @{
            Authorization = "Bearer $($entry.User.accessToken)"
            'Queue-Token' = $entry.QueueToken
            'Idempotency-Key' = [guid]::NewGuid().ToString()
        }
        $participation = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/group-buys/$groupBuyId/participations" `
            -Headers @{ Authorization = $headers.Authorization } `
            -ContentType 'application/json' `
            -Body '{"quantity":1}'

        $participationId = $participation.result.participationId
        $payment = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/participations/$participationId/payment" `
            -Headers $headers `
            -ContentType 'application/json' `
            -Body '{"paymentMethod":"CARD"}'

        Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/payment-attempts/$($payment.paymentAttemptId)/confirm" `
            -Headers @{ Authorization = $headers.Authorization } `
            -ContentType 'application/json' `
            -Body (@{
                paymentKey = "local-demo-$([guid]::NewGuid())"
                pgOrderId = $payment.pgOrderId
                amount = $payment.amount
            } | ConvertTo-Json -Compress) | Out-Null
    }

    # 완료된 사용자는 Redis에서 대기열 토큰까지 삭제됩니다.
    # 이후 상태 조회에 다시 포함하면 issueToken()이 새 대기열 항목으로 재등록하므로 제외합니다.
    $completedEmails = @($admitted | ForEach-Object { $_.User.email })
    $remainingUsers = @($remainingUsers | Where-Object { $_.email -notin $completedEmails })

    # Prometheus가 15초마다 수집하므로, 각 10명 배출 단계를 그래프에서 분리해 볼 수 있게 기다립니다.
    Write-Host "  다음 배치 전 $BatchIntervalSeconds초 대기 (Prometheus 수집 반영)..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $BatchIntervalSeconds

    $after = Get-QueueSnapshot -Users $remainingUsers
    $waiting = @($after | Where-Object { $_.Status -eq 'WAITING' }).Count
    $nextAdmitted = @($after | Where-Object { $_.Status -eq 'ADMITTED' }).Count
    Write-Host "  완료: waiting=$waiting, admitted=$nextAdmitted"
}
