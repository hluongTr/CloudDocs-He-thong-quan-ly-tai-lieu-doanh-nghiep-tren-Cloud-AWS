$ErrorActionPreference = 'Stop'
$node = (Get-Command node.exe -ErrorAction Stop).Source
& $node (Join-Path $PSScriptRoot 'serve-frontend.cjs')
if ($LASTEXITCODE -ne 0) { throw "Frontend server exited with code $LASTEXITCODE" }
