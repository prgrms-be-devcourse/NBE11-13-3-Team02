$ErrorActionPreference = 'Stop'

function Read-RedisResponse {
    param([System.IO.StreamReader]$Reader)

    $prefix = $Reader.Read()
    if ($prefix -lt 0) { throw 'Redis 연결이 예기치 않게 종료되었습니다.' }

    switch ([char]$prefix) {
        '+' { return $Reader.ReadLine() }
        ':' { return [long]$Reader.ReadLine() }
        '-' { throw "Redis 오류: $($Reader.ReadLine())" }
        '$' {
            $length = [int]$Reader.ReadLine()
            if ($length -lt 0) { return $null }
            $buffer = New-Object char[] $length
            $offset = 0
            while ($offset -lt $length) {
                $read = $Reader.Read($buffer, $offset, $length - $offset)
                if ($read -le 0) { throw 'Redis bulk 응답을 끝까지 읽지 못했습니다.' }
                $offset += $read
            }
            $null = $Reader.Read()
            $null = $Reader.Read()
            return -join $buffer
        }
        '*' {
            $count = [int]$Reader.ReadLine()
            if ($count -lt 0) { return $null }
            $values = @()
            for ($index = 0; $index -lt $count; $index++) {
                $values += Read-RedisResponse $Reader
            }
            return $values
        }
        default { throw "알 수 없는 Redis 응답 형식: $([char]$prefix)" }
    }
}

function Invoke-RedisCommand {
    param([string[]]$Arguments)

    $client = [System.Net.Sockets.TcpClient]::new('127.0.0.1', 6379)
    try {
        $stream = $client.GetStream()
        $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write('*' + $Arguments.Count + "`r`n")
            foreach ($argument in $Arguments) {
                $byteCount = [System.Text.Encoding]::UTF8.GetByteCount($argument)
                $writer.Write('$' + $byteCount + "`r`n" + $argument + "`r`n")
            }
            $writer.Flush()
            return Read-RedisResponse $reader
        } finally {
            $writer.Dispose()
            $reader.Dispose()
        }
    } finally {
        $client.Dispose()
    }
}

$groupIds = @(Invoke-RedisCommand @('SMEMBERS', 'queue:groups'))
foreach ($groupBuyId in $groupIds) {
    if ([string]::IsNullOrWhiteSpace($groupBuyId)) { continue }
    $keys = @(
        "queue:waiting:$groupBuyId",
        "queue:active:$groupBuyId",
        "queue:confirming:$groupBuyId",
        "queue:tokens:$groupBuyId",
        "queue:attempts:$groupBuyId",
        "queue:sequence:$groupBuyId"
    )
    Invoke-RedisCommand (@('DEL') + $keys) | Out-Null
}
Invoke-RedisCommand @('DEL', 'queue:groups') | Out-Null

Write-Host "결제 대기열 초기화 완료: $($groupIds.Count)개 공동구매"
