$ErrorActionPreference = 'Stop'

# Resolve the checkout from this script regardless of the caller's working directory.
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repositoryRoot
try {
    & .\gradlew.bat run --no-daemon
    $previewExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $previewExitCode
