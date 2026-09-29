<#
.SYNOPSIS
    Builds the Windows release files into dist\release:
      Devava-Notes-<version>-windows-x64-setup.exe      installer (needs the WiX Toolset 3.x)
      Devava-Notes-<version>-windows-x64-portable.zip   the app image, zipped
      SHA256SUMS.txt                                    checksums (sha256sum format)
      RELEASE_NOTES.md                                  the text of the release (release-notes.ps1)

.DESCRIPTION
    The version comes from pom.xml and must have a "## [<version>]" section in CHANGELOG.md.
    The workflow .github/workflows/release.yml runs this when the tag v<version> is pushed.

.EXAMPLE
    .\packaging\release.ps1
    .\packaging\release.ps1 -SkipInstaller      # only the ZIP (no WiX needed)
#>
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$SkipInstaller,
    [string]$Output = (Join-Path (Split-Path $PSScriptRoot -Parent) "dist\release")
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$appName = "Devava Notes"
$version = ([xml](Get-Content (Join-Path $projectDir "pom.xml"))).project.version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "pom.xml version '$version' is not a release version (expected x.y.z)." }
$changelog = Get-Content (Join-Path $projectDir "CHANGELOG.md") -Raw -Encoding UTF8
if ($changelog -notmatch "(?m)^## \[$([regex]::Escape($version))\]") {
    throw "CHANGELOG.md has no '## [$version]' section. Add one before releasing."
}

# --- Build ---------------------------------------------------------------------
$build = Join-Path $PSScriptRoot "build-windows.ps1"
$stage = Join-Path $projectDir "target\package\dist"
$zipName = "Devava-Notes-$version-windows-x64-portable.zip"
$setupName = "Devava-Notes-$version-windows-x64-setup.exe"

if (Test-Path $Output) { Remove-Item -Recurse -Force $Output }
New-Item -ItemType Directory -Force -Path $Output | Out-Null

& $build -JdkHome $JdkHome -Type app-image -Destination $stage

# Portable ZIP of the app image: one top-level folder, forward slashes in the entry names
# (ZipFile::CreateFromDirectory would use backslashes under Windows PowerShell 5.1).
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zipPath = Join-Path $Output $zipName
$root = (Get-Item (Join-Path $stage $appName)).FullName
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem $root -Recurse -File) {
        $entry = "$appName/" + $file.FullName.Substring($root.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $entry,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}
Write-Host "Zipped: $zipPath"

if (-not $SkipInstaller) {
    & $build -JdkHome $JdkHome -Type exe -ReuseImage -Destination $stage
    Move-Item (Join-Path $stage "$appName-$version.exe") (Join-Path $Output $setupName) -Force
    Write-Host "Installer: $(Join-Path $Output $setupName)"
}

# --- Checksums and release notes ---------------------------------------------------
& (Join-Path $PSScriptRoot "release-notes.ps1") -Dir $Output -Version $version

Write-Host ""
Write-Host "Release files for $appName $version in $Output`:"
Get-ChildItem $Output | ForEach-Object { Write-Host ("  {0,-50} {1,10:N0} KB" -f $_.Name, ($_.Length / 1KB)) }
