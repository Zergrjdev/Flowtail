# Check prerequisites on Windows. Run: powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
param([switch]$Check)
$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')
$match = Select-String -Path 'versions.toml' -Pattern '^java_runtime\s*=\s*(\d+)'
if (-not $match) { Write-Host '[ERROR] Could not read java_runtime from versions.toml.'; exit 1 }
$need = [int]$match.Matches[0].Groups[1].Value
$ok = $true

if (Get-Command git -ErrorAction SilentlyContinue) { Write-Host "[ok] $(git --version)" }
else {
  $ok = $false; Write-Host '[MISSING] Git. Install it with: winget install Git.Git'
  if (-not $Check -and (Get-Command winget -ErrorAction SilentlyContinue)) {
    $answer = Read-Host 'Install Git now with winget? [y/N]'
    if ($answer -match '^[yY]$') { winget install --id Git.Git --exact --accept-package-agreements --accept-source-agreements }
  }
}

$javaPath = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe' }
if (-not $javaPath -and (Test-Path ".tools\jdk-$need\bin\java.exe")) { $javaPath = ".tools\jdk-$need\bin\java.exe" }
if (-not $javaPath -and (Get-Command java -ErrorAction SilentlyContinue)) { $javaPath = (Get-Command java).Source }
$javaOk = $false
if ($javaPath) {
  $line = (& $javaPath -version 2>&1 | Select-Object -First 1).ToString()
  if ($line -match '"(\d+)') { $version = [int]$Matches[1]; if ($version -ge $need) { $javaOk = $true; Write-Host "[ok] Java $version at $javaPath" } }
}
if (-not $javaOk) {
  $ok = $false; Write-Host "[MISSING] Java $need or newer (required by this Minecraft version). Install: winget install EclipseAdoptium.Temurin.$need.JDK"
  if (-not $Check -and (Get-Command winget -ErrorAction SilentlyContinue)) {
    $answer = Read-Host "Install Temurin Java $need now with winget? [y/N]"
    if ($answer -match '^[yY]$') { winget install --id "EclipseAdoptium.Temurin.$need.JDK" --exact --accept-package-agreements --accept-source-agreements }
  }
}

if (Test-Path 'upstream\Paper') {
  if (Test-Path 'upstream\Paper\gradlew.bat') { Write-Host '[ok] Gradle wrapper found in upstream\Paper (no separate Gradle install needed).' }
  else { $ok = $false; Write-Host '[MISSING] Paper Gradle wrapper is missing. Re-clone upstream\Paper, then rerun setup.' }
} else { Write-Host '[info] Gradle wrapper will be provided by Paper when scripts/build-upstream-paper.sh clones it.' }

if ($ok) { Write-Host "`nPrerequisites are ready. Next: scripts\build-upstream-paper.sh (run in Git Bash or WSL)."; exit 0 }
Write-Host "`nSetup is incomplete. Follow each [MISSING] instruction, open a new terminal if you installed software, then run setup again."
exit 1
