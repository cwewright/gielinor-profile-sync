[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ClientArguments = @()
)

$ErrorActionPreference = "Stop"
$PreviewRoot = Join-Path $PSScriptRoot "Preview"
$ManifestPath = Join-Path $PreviewRoot "PREVIEW-MANIFEST.json"

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "The developer-preview manifest is missing. Reinstall the preview package."
}

$Manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$ApplicationJar = Join-Path (Join-Path $PreviewRoot "lib") ([string]$Manifest.applicationJar.file)
if (-not (Test-Path -LiteralPath $ApplicationJar -PathType Leaf)) {
    throw "The developer-preview application is missing. Reinstall the preview package."
}

$JavaCandidates = @()
foreach ($JavaRoot in @($env:GIELINOR_PREVIEW_JAVA_HOME, $env:JAVA_HOME)) {
    if (-not [string]::IsNullOrWhiteSpace($JavaRoot)) {
        $JavaCandidates += Join-Path $JavaRoot "bin\java.exe"
    }
}
$PathJava = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if ($PathJava) { $JavaCandidates += $PathJava.Source }

$JavaPath = $null
foreach ($Candidate in @($JavaCandidates | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $Candidate -PathType Leaf)) { continue }
    $VersionOutput = (& $Candidate -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { continue }
    if ($VersionOutput -match 'version\s+"11(?:\.|\+|\")') {
        $JavaPath = $Candidate
        break
    }
}

if (-not $JavaPath) {
    throw "Java 11 was not found. Install the Java 11 preview runtime supplied with Sailor's Log, or set GIELINOR_PREVIEW_JAVA_HOME to a Java 11 runtime."
}

$Arguments = @(
    '-ea',
    '-jar',
    $ApplicationJar,
    '--developer-mode',
    '--debug',
    '--profile',
    'gielinor-profile-sync-preview'
) + $ClientArguments

& $JavaPath @Arguments
exit $LASTEXITCODE
