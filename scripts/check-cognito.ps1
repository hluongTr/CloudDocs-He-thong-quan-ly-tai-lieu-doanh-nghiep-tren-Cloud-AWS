# Read-only diagnostic: public metadata and authorization redirect. No sign-in or AWS changes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$projectRoot = Split-Path -Parent $PSScriptRoot
$source = Get-Content -LiteralPath (Join-Path $projectRoot 'FrontEnd\js\cognito-config.js') -Raw
$domain = [regex]::Match($source, 'domain:\s*"([^"]+)"').Groups[1].Value
$clientId = [regex]::Match($source, 'clientId:\s*"([^"]+)"').Groups[1].Value
$redirectUri = [regex]::Match($source, 'redirectUri:\s*"([^"]+)"').Groups[1].Value
if (!$domain -or !$clientId -or !$redirectUri) { throw 'Missing public Cognito configuration.' }
$random = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($random)
$rng.Dispose()
$challenge = [Convert]::ToBase64String($random).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$url = "$domain/oauth2/authorize?response_type=code&client_id=$([Uri]::EscapeDataString($clientId))&redirect_uri=$([Uri]::EscapeDataString($redirectUri))&scope=openid&state=configuration-check&code_challenge_method=S256&code_challenge=$challenge"
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$http = New-Object System.Net.Http.HttpClient($handler)
$http.Timeout = [TimeSpan]::FromSeconds(20)
try {
    $response = $http.GetAsync($url).GetAwaiter().GetResult()
    $location = [string]$response.Headers.Location
    Write-Host "HTTP: $([int]$response.StatusCode)"
    Write-Host "Configured callback: $redirectUri"
    if ($location -match 'redirect_mismatch') {
        Write-Host 'AWS returned redirect_mismatch.' -ForegroundColor Red
        Write-Host "Add exactly $redirectUri to Allowed callback URLs in this app client's Login pages."
        exit 1
    }
    if ($location -match '(?:[?&])error=([^&]+)') {
        Write-Host "AWS error: $([Uri]::UnescapeDataString($Matches[1]))" -ForegroundColor Red
        exit 1
    }
    if ($location -and ([Uri]$location).AbsolutePath -eq '/login') {
        Write-Host 'AWS redirected to /login. Callback configuration passed this check.' -ForegroundColor Green
        Write-Host 'Finish the end-to-end sign-in from the CloudDocs login button in your browser.'
    } else {
        Write-Host 'No recognized login redirect. Check Cognito Login pages and browser response.'
    }
} finally {
    $http.Dispose()
    $handler.Dispose()
}
