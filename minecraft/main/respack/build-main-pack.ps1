$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Remove-PathIfExists {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path $Path) {
        Remove-Item $Path -Recurse -Force
    }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..\..\..')).Path

$GeneratorDir = Join-Path $ScriptDir 'items-respack-generator'
$MergerDir = Join-Path $ScriptDir 'ResourcePackMerger'
$PacksDir = Join-Path $ScriptDir 'packs'
$ItemsDir = (Resolve-Path (Join-Path $ScriptDir '..\data\items')).Path

$GeneratedDir = Join-Path $PacksDir 'generated'
$MergedDir = Join-Path $PacksDir 'main-pack'
$FinalZip = Join-Path $PacksDir 'main-pack.zip'

$WebPacksDir = Join-Path $RepoRoot 'services\web\public\packs'
$WebZip = Join-Path $WebPacksDir 'main.zip'

Write-Host '==> Generating resource pack from item definitions'
Push-Location $GeneratorDir
try {
    if (-not (Test-Path '.\node_modules')) {
        & npm 'ci'
    }

    Remove-PathIfExists $GeneratedDir

    & npm 'run' 'generate' '--' `
        '--source' $ItemsDir `
        '--vanilla-armor' '.\vanilla_armor_assets' `
        '--output' $GeneratedDir
}
finally {
    Pop-Location
}

Write-Host '==> Building ResourcePackMerger'
Push-Location $MergerDir
try {
    if (Test-Path '.\mvnw.cmd') {
        & .\mvnw.cmd '-q' '-DskipTests' 'package'
    }
    elseif (Test-Path '.\mvnw') {
        & .\mvnw '-q' '-DskipTests' 'package'
    }
    else {
        & mvn '-q' '-DskipTests' 'package'
    }

    $MergerCandidates = @(
        Get-ChildItem '.\target' -Filter '*.jar' |
            Where-Object { $_.Name -notlike 'original-*' } |
            Sort-Object LastWriteTime -Descending
    )

    if ($MergerCandidates.Count -eq 0) {
        throw "Could not find a built ResourcePackMerger jar in $MergerDir\target"
    }

    $MergerJar = $MergerCandidates[0].FullName
}
finally {
    Pop-Location
}

Remove-PathIfExists $MergedDir
Remove-PathIfExists $FinalZip
New-Item -ItemType Directory -Force -Path $PacksDir | Out-Null
New-Item -ItemType Directory -Force -Path $WebPacksDir | Out-Null

$MergeInputs = @(
    Get-ChildItem $PacksDir |
        Where-Object {
            ($_.PSIsContainer -or $_.Extension -eq '.zip') -and
            $_.Name -ne 'main-pack' -and
            $_.Name -ne 'main-pack.zip'
        } |
        Sort-Object Name
)

if ($MergeInputs.Count -eq 0) {
    throw "No input packs found in $PacksDir"
}

Write-Host '==> Merging packs'
$MergeInputs | ForEach-Object { Write-Host " - $($_.FullName)" }

$MergerArgs = @($MergeInputs | ForEach-Object { $_.FullName }) + @($MergedDir)
& java '-jar' $MergerJar @MergerArgs

Write-Host '==> Creating zip archive'
& jar '--create' '--file' $FinalZip '--no-manifest' '-C' $MergedDir '.'

Write-Host '==> Publishing zip for the website'
Copy-Item $FinalZip $WebZip -Force

Write-Host "Done:"
Write-Host " - canonical archive: $FinalZip"
Write-Host " - served archive:    $WebZip"