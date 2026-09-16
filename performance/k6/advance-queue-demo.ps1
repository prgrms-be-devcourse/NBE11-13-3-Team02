param(
    [int]$BatchCount = 1,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$dataPath = Join-Path $PSScriptRoot 'generated\queue-load-test.json'

if (-not (Test-Path -LiteralPath $dataPath)) {
    throw "테스트 데이터가 없습니다. .\\performance\\k6\\prepare-queue-load-test.ps1 를 먼저 실행하세요."
}

$testData = Get-Content -Raw -LiteralPath $dataPath | ConvertFrom-Json
$groupBuyId = $testData.groupBuyId

function Get-QueueSnapshot {
    $snapshot = @()

    foreach ($user in $testData.users) {
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

for ($batch = 1; $batch -le $BatchCount; $batch++) {
    Write-Host "[$batch/$BatchCount] 현재 ADMITTED 사용자 10명을 찾는 중..."
    $snapshot = Get-QueueSnapshot
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

    # QueueAdmissionScheduler가 비어 있는 슬롯을 감지해 다음 10명을 입장시킬 시간을 줍니다.
    Start-Sleep -Seconds 3

    $after = Get-QueueSnapshot
    $waiting = @($after | Where-Object { $_.Status -eq 'WAITING' }).Count
    $nextAdmitted = @($after | Where-Object { $_.Status -eq 'ADMITTED' }).Count
    Write-Host "  완료: waiting=$waiting, admitted=$nextAdmitted"
}
