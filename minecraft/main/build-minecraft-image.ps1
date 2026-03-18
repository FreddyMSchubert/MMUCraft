param(
    [Parameter(Mandatory = $true)]
    [string]$ExpectedRef
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModDir = Join-Path $ScriptDir 'mod'
$RespackDir = Join-Path $ScriptDir 'respack'

Write-Host '==> Building Fabric mod'
Push-Location $ModDir
try {
    & .\gradlew.bat runDatagen
	& .\gradlew.bat build
}
finally {
    Pop-Location
}

Write-Host '==> Building merged resource pack'
& (Join-Path $RespackDir 'build-main-pack.ps1')

Write-Host "==> Building minecraft image: $ExpectedRef"
& docker 'build' '-t' $ExpectedRef '-f' (Join-Path $ScriptDir 'Dockerfile') $ScriptDir