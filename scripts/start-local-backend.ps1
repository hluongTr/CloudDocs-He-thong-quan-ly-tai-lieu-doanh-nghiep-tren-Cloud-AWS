param(
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'company_documents',
    [string]$DatabaseUser = 'root',
    [ValidateSet('ACTIVE', 'PENDING_APPROVAL')]
    [string]$NewUserStatus = 'ACTIVE'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectRoot 'Back_end\target\clouddocs-backend-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jar)) {
    throw 'Missing backend JAR. Build Back_end with Java 21 and mvn package first.'
}
$java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} elseif (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-21\bin\java.exe') {
    'C:\Program Files\Java\jdk-21\bin\java.exe'
} else { (Get-Command java.exe -ErrorAction Stop).Source }
$configSource = Get-Content -LiteralPath (Join-Path $projectRoot 'FrontEnd\js\cognito-config.js') -Raw
$callback = [regex]::Match($configSource, 'redirectUri:\s*"([^"\r\n]+)"').Groups[1].Value
if (!$callback) { throw 'Missing frontend redirectUri configuration.' }
$frontendOrigin = ([Uri]$callback).GetLeftPart([System.UriPartial]::Authority)
$env:DB_HOST = $DatabaseHost
$env:DB_PORT = [string]$DatabasePort
$env:DB_NAME = $DatabaseName
$env:DB_USERNAME = $DatabaseUser
$env:COGNITO_ISSUER_URI = 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_izadKavHA'
$env:COGNITO_CLIENT_ID = '5d1jefk575idgerqa8jbsd2b80'
$env:COGNITO_NEW_USER_STATUS = $NewUserStatus
$env:CORS_ALLOWED_ORIGINS = $frontendOrigin
$previousPassword = $env:DB_PASSWORD
$securePassword = Read-Host "MySQL password for $DatabaseUser (hidden input)" -AsSecureString
$credential = New-Object System.Management.Automation.PSCredential($DatabaseUser, $securePassword)
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
Push-Location (Join-Path $projectRoot 'Back_end')
try {
    Write-Host 'CloudDocs backend: http://localhost:8080 (Cognito, local environment)'
    & $java -jar $jar '--spring.profiles.active=local' '--server.port=8080'
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally {
    $env:DB_PASSWORD = $previousPassword
    $securePassword.Dispose()
    Pop-Location
}
