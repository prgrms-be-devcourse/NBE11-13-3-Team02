[CmdletBinding()]
param(
    [ValidateRange(1, 5000)] [int]$UserCount = 500,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [int]$ProductId = 1,
    [string]$Password = 'LoadTest!1234'
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$outputDirectory = Join-Path $scriptRoot 'generated'
$outputPath = Join-Path $outputDirectory 'queue-load-test.json'
$runId = Get-Date -Format 'yyyyMMddHHmmss'

function Invoke-JsonPost {
    param([string]$Path, [object]$Body, [hashtable]$Headers = @{})
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -ContentType 'application/json' `
        -Headers $Headers -Body ($Body | ConvertTo-Json -Compress)
}

New-Item -ItemType Directory -Force $outputDirectory | Out-Null
Write-Host "[1/3] 판매자 로그인 및 정원 $UserCount 공동구매 생성 중..."
$sellerLogin = Invoke-JsonPost '/api/auth/login' @{ email = 'seller1@test.com'; password = '1234' }
$sellerHeaders = @{ Authorization = "Bearer $($sellerLogin.accessToken)" }
$now = Get-Date
$groupBuy = Invoke-JsonPost '/api/group-buys' @{
    productId = $ProductId
    targetCount = $UserCount
    discountRate = 0.1
    openAt = $now.AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ss')
    deadline = $now.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
} $sellerHeaders
$groupBuyId = $groupBuy.result.groupBuyId
if ($null -eq $groupBuyId) { throw '정원 500 공동구매 생성에 실패했습니다.' }

Write-Host "[2/3] 로컬 테스트 구매자 $UserCount명 및 JWT 일괄 생성 중..."
$fixture = Invoke-JsonPost '/api/dev/load-test/buyers' @{
    userCount = $UserCount
    emailPrefix = "queue-load-$runId"
    password = $Password
} $sellerHeaders
$users = $fixture.users

if ($users.Count -ne $UserCount) {
    throw "테스트 구매자 생성 수가 일치하지 않습니다. 기대: $UserCount, 실제: $($users.Count)"
}

$json = [PSCustomObject]@{
    createdAt = (Get-Date).ToString('o')
    groupBuyId = $groupBuyId
    targetCount = $UserCount
    password = $Password
    users = $users
} | ConvertTo-Json -Depth 4

# Windows PowerShell의 UTF-8 BOM을 제거해 k6의 JSON 파서와 호환되게 저장합니다.
[System.IO.File]::WriteAllText($outputPath, $json, [System.Text.UTF8Encoding]::new($false))

Write-Host "[3/3] 준비 완료: groupBuyId=$groupBuyId"
Write-Host "tokens=$outputPath"
