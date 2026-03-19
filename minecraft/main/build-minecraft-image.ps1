param(
    [Parameter(Mandatory = $true)]
    [string]$ExpectedRef
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModDir = Join-Path $ScriptDir 'mod'

Write-Host '==> Validating and staging item data'
python (Join-Path $ScriptDir 'build\stage_item_data.py') --root $ScriptDir

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
python (Join-Path $ScriptDir 'respack\build-main-pack.py')

Write-Host "==> Building minecraft image: $ExpectedRef"
& docker 'build' '-t' $ExpectedRef '-f' (Join-Path $ScriptDir 'Dockerfile') $ScriptDir