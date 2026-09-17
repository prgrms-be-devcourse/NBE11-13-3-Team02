$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Get-GitBash {
    $candidates = @(
        'C:\Program Files\Git\bin\bash.exe',
        'C:\Program Files\Git\usr\bin\bash.exe'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    throw 'Git Bash를 찾지 못했습니다. Git for Windows를 설치한 뒤 다시 실행해주세요.'
}

Push-Location $projectRoot
try {
    # 최신 full compose는 JWT_SECRET을 필수로 검증하므로 시작과 동일하게
    # backend/frontend의 Infisical 시크릿을 병합한 상태에서 중지합니다.
    $bash = Get-GitBash
    & $bash './run-docker-full.sh' stop
    if ($LASTEXITCODE -ne 0) { throw '전체 Docker 환경 종료에 실패했습니다. infisical login 상태를 확인해주세요.' }
    Write-Host 'gachisa-full 전체 Docker 환경을 중지했습니다.' -ForegroundColor Green
} finally {
    Pop-Location
}
