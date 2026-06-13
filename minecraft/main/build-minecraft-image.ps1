param(
    [Parameter(Mandatory = $true)]
    [string]$ExpectedRef
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
$ModDir = Join-Path $ScriptDir 'mod'
$Dockerfile = Join-Path $ScriptDir 'Dockerfile'
$GeneratedServerProperties = Join-Path $ScriptDir 'server.properties.generated'
$JarDir = Join-Path $ModDir 'build\libs'

Write-Host '==> Validating and staging item data'
python (Join-Path $ScriptDir 'stage_item_data.py') --root $ScriptDir

Write-Host '==> Building Fabric mod'
Push-Location $ModDir
try {
    & .\gradlew.bat generateProto
    if ($LASTEXITCODE -ne 0) { throw "generateProto failed with exit code $LASTEXITCODE" }

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
if ($LASTEXITCODE -ne 0 -and (Test-Path -LiteralPath $GeneratedServerProperties -PathType Leaf)) {
    Write-Warning "resource pack build failed with exit code $LASTEXITCODE; reusing existing $GeneratedServerProperties"
}

if (-not (Test-Path -LiteralPath $GeneratedServerProperties -PathType Leaf)) {
    throw "resource pack build did not create $GeneratedServerProperties"
}

$JarFiles = @(
    Get-ChildItem -LiteralPath $JarDir -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-sources.jar' }
)
if ($JarFiles.Count -eq 0) {
    throw "Fabric mod build did not create a runtime jar in $JarDir"
}

$DockerContext = Join-Path ([System.IO.Path]::GetTempPath()) ("kubecraft-minecraft-image-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $DockerContext 'mod\build\libs') -Force | Out-Null

Write-Host "==> Building minecraft image: $ExpectedRef"
try {
    Copy-Item -LiteralPath $Dockerfile -Destination (Join-Path $DockerContext 'Dockerfile')
    Copy-Item -LiteralPath $GeneratedServerProperties -Destination (Join-Path $DockerContext 'server.properties.generated')
    foreach ($JarFile in $JarFiles) {
        Copy-Item -LiteralPath $JarFile.FullName -Destination (Join-Path $DockerContext 'mod\build\libs')
    }

    & docker 'build' '-t' $ExpectedRef '-f' (Join-Path $DockerContext 'Dockerfile') $DockerContext
    if ($LASTEXITCODE -ne 0) { throw "docker build failed with exit code $LASTEXITCODE" }
}
finally {
    if (Test-Path -LiteralPath $DockerContext) {
        Remove-Item -LiteralPath $DockerContext -Recurse -Force
    }
}
