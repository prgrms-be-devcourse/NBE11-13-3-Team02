$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'queue-unique-stress.ps1'

# 1명의 구매자가 대기열 토큰을 발급받아 ADMITTED 상태가 되는지 확인합니다.
# 인증, Redis 연결, 공동구매 조회, 대기열 API의 기본 연결 상태를 빠르게 검증합니다.
& $runner -Stages @(1) -CorrectnessOnly -TestType smoke -K6ScriptName 'queue-500-users.js'
