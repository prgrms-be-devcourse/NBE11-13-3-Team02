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

function Invoke-FullCompose([string[]]$ComposeArgs) {
    $bash = Get-GitBash
    & $bash './run-docker-full.sh' @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'Infisical 시크릿 병합 또는 전체 Docker Compose 실행에 실패했습니다. infisical login 상태를 확인해주세요.'
    }
}

function Wait-ForHttp([string]$Name, [string]$Uri, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "$Name 준비를 $TimeoutSeconds 초 안에 확인하지 못했습니다: $Uri"
}

Push-Location $projectRoot
try {
    # dev-start로 띄운 로컬 프로세스와 observability 컨테이너가 포트를 점유한 경우
    # full 환경으로 전환한 뒤 동일한 테스트 URL을 그대로 사용할 수 있게 정리합니다.
    $devStateFile = Join-Path $projectRoot '.dev-runtime\processes.json'
    if (Test-Path -LiteralPath $devStateFile) {
        & (Join-Path $projectRoot 'dev-stop.ps1')
        if ($LASTEXITCODE -ne 0) { throw '개발 환경 종료에 실패했습니다.' }
    } else {
        $devContainerIds = @(& docker compose -f observability/docker-compose.yml ps --status running -q)
        if ($devContainerIds.Count -gt 0) {
            & docker compose -f observability/docker-compose.yml stop
            if ($LASTEXITCODE -ne 0) { throw '개발용 관측 환경 종료에 실패했습니다.' }
        }
    }

    # backend/frontend의 서로 다른 Infisical 프로젝트 시크릿을 병합한 뒤 실행합니다.
    # 일부 컨테이너가 이미 실행 중이어도 현재 소스와 Compose 설정을 항상 반영합니다.
    Invoke-FullCompose @('up', '-d', '--build', '--remove-orphans')

    Write-Host '[준비 확인] Backend...' -ForegroundColor Cyan
    Wait-ForHttp 'Backend' 'http://127.0.0.1:8080/actuator/health' 180
    Write-Host '[준비 확인] Prometheus...' -ForegroundColor Cyan
    Wait-ForHttp 'Prometheus' 'http://127.0.0.1:9090/-/ready' 60
    Write-Host '[준비 확인] Grafana...' -ForegroundColor Cyan
    Wait-ForHttp 'Grafana' 'http://127.0.0.1:3000/api/health' 60

    Write-Host 'gachisa-full 전체 Docker 환경이 실행되었습니다.' -ForegroundColor Green
    Write-Host 'Frontend  : http://localhost:5173'
    Write-Host 'Backend   : http://localhost:8080'
    Write-Host 'Prometheus: http://localhost:9090'
    Write-Host 'Grafana   : http://localhost:3000 (admin / admin)'
    Write-Host 'Grafana 대시보드는 부하 테스트와 결제·환불 안정성, 총 2개입니다.'
} finally {
    Pop-Location
}
