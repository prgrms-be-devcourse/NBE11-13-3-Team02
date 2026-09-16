$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $projectRoot
try {
    $runningContainerIds = @(& docker compose -f docker-compose.full.yml ps --status running -q)
    if ($runningContainerIds.Count -eq 0) {
        & docker compose -f docker-compose.full.yml up -d --build
        if ($LASTEXITCODE -ne 0) { throw '전체 Docker 환경 시작에 실패했습니다.' }
        Write-Host 'gachisa-full 전체 Docker 환경이 실행되었습니다.' -ForegroundColor Green
    } else {
        Write-Host 'gachisa-full은 이미 실행 중입니다.' -ForegroundColor Yellow
    }
} finally {
    Pop-Location
}
