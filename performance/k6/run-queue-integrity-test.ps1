param(
    [int]$UserCount = 500,
    [int]$MaxConnectionAttempts = 5
)

$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'queue-unique-stress.ps1'
if ($UserCount -lt 10) { throw '사용자 수는 최소 10명이어야 합니다.' }
if ($MaxConnectionAttempts -lt 1) { throw '재시도 횟수는 최소 1회여야 합니다.' }

# 연결 거부는 인프라 진입 문제이므로 같은 사용자의 요청만 짧게 재시도합니다.
# 이 테스트의 판정 대상은 10명 입장·나머지 대기라는 대기열 규칙입니다.
$previousAttempts = $env:QUEUE_MAX_CONNECTION_ATTEMPTS
$env:QUEUE_MAX_CONNECTION_ATTEMPTS = $MaxConnectionAttempts
try {
    & $runner -Stages @($UserCount) -CorrectnessOnly -TestType integrity -K6ScriptName 'queue-integrity-users.js'
} finally {
    if ($null -eq $previousAttempts) {
        Remove-Item Env:QUEUE_MAX_CONNECTION_ATTEMPTS -ErrorAction SilentlyContinue
    } else {
        $env:QUEUE_MAX_CONNECTION_ATTEMPTS = $previousAttempts
    }
}
