param(
    [Parameter(Mandatory = $true)]
    [string]$ExpectedRef
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModDir = Join-Path $ScriptDir 'mod'

Write-Host '==> Validating and staging item data'
python (Join-Path $ScriptDir 'stage_item_data.py') --root $ScriptDir

Write-Host '==> Building Fabric mod'
Push-Location $ModDir
try {
	& .\gradlew.bat runDatagen
	if ($LASTEXITCODE -ne 0) { throw "runDatagen failed with exit code $LASTEXITCODE" }

	& .\gradlew.bat build
	if ($LASTEXITCODE -ne 0) { throw "build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}

Write-Host '==> Building merged resource pack'
python (Join-Path $ScriptDir 'respack\build-main-pack.py')

Write-Host "==> Building minecraft image: $ExpectedRef"
& docker 'build' '-t' $ExpectedRef '-f' (Join-Path $ScriptDir 'Dockerfile') $ScriptDir