$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not $env:VNPAY_TMN_CODE -or -not $env:VNPAY_HASH_SECRET) {
        throw 'Set VNPAY_TMN_CODE and VNPAY_HASH_SECRET in the process environment first.'
    }
    New-Item -ItemType Directory -Force target/vnpay-diagnostic/classes | Out-Null
    # Compile the current source directly; Maven output may be stale.
    javac -encoding UTF-8 -d target/vnpay-diagnostic/classes src/main/java/com/stockflow/payment/util/VNPayUtil.java scripts/VNPayDiagnostic.java
    if ($LASTEXITCODE -ne 0) { throw 'Diagnostic compilation failed.' }
    java -cp target/vnpay-diagnostic/classes VNPayDiagnostic
    if ($LASTEXITCODE -ne 0) { throw 'URL generation failed.' }
    $results = foreach ($variant in 1..4) {
        $url = [IO.File]::ReadAllText((Join-Path $projectRoot "target/vnpay-diagnostic/variant-$variant-url.txt"))
        try {
            # Windows trust store is used; certificate validation remains enabled.
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
            $finalUrl = $response.BaseResponse.ResponseUri.AbsoluteUri
            $body = $response.Content
            [IO.File]::WriteAllText((Join-Path $projectRoot "target/vnpay-diagnostic/variant-$variant-response.html"), $body)
            $code = if ($finalUrl -match '[?&]code=([^&]+)') { $Matches[1] } else { $null }
            $plain = [Net.WebUtility]::HtmlDecode([regex]::Replace($body, '<[^>]+>', ' '))
            [pscustomobject]@{
                variant = $variant
                httpStatus = [int]$response.StatusCode
                finalUrl = $finalUrl
                errorCode = $code
                invalidSignature = ($code -eq '70' -or $plain -match 'Sai chữ ký')
                # An error page also contains a form: absence of code=70 alone is not acceptance.
                paymentPageCandidate = ($finalUrl -notmatch '/Error\.' -and -not $code -and $body -match '<form')
            }
        } catch {
            [pscustomobject]@{variant=$variant; transportError=$_.Exception.GetType().Name; paymentPageCandidate=$false}
        }
    }
    $results | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 target/vnpay-diagnostic/results.json
    $results | ConvertTo-Json -Depth 4
} finally { Pop-Location }
