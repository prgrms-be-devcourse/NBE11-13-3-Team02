$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $projectRoot
try {
    $runningContainerIds = @(& docker compose -f docker-compose.full.yml ps --status running -q)
    if ($runningContainerIds.Count -eq 0) {
        Write-Host 'gachisa-full은 이미 중지되어 있습니다.'
    } else {
        # 컨테이너를 삭제(down)하지 않고 중지(stop)만 합니다.
        # 그래서 Docker Desktop에서 gachisa-full 묶음이 계속 보이고 ▶ 버튼으로 다시 시작할 수 있습니다.
        & docker compose -f docker-compose.full.yml stop
        if ($LASTEXITCODE -ne 0) { throw '전체 Docker 환경 종료에 실패했습니다.' }
        Write-Host 'gachisa-full 전체 Docker 환경을 중지했습니다.' -ForegroundColor Green
    }
} finally {
    Pop-Location
}
