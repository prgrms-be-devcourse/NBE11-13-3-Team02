param(
    [int[]]$Stages = @(100, 200, 500, 600, 700)
)

$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'queue-unique-stress.ps1'

# 재시도 없이 최초 동시 요청만 보냅니다.
# 어느 단계부터 사용자가 서버 진입 전에 탈락하는지 확인하는 시스템 수용량 테스트입니다.
& $runner -Stages $Stages -CorrectnessOnly -TestType capacity -K6ScriptName 'queue-500-users.js'
