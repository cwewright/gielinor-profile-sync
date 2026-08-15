[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"

function Assert-Preview {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Preview distribution verification failed: $Message" }
}

function Get-Sha256 {
    param([string]$FilePath)

    $Stream = [IO.File]::OpenRead($FilePath)
    $Digest = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($Digest.ComputeHash($Stream))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $Digest.Dispose()
        $Stream.Dispose()
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$ArchivePath = (Resolve-Path -LiteralPath $Path).Path
Assert-Preview ($ArchivePath.EndsWith(".zip", [StringComparison]::OrdinalIgnoreCase)) "the artifact is not a ZIP file."

$TempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$FixtureName = "gielinor-preview-verify-" + [Guid]::NewGuid().ToString("N")
$FixtureRoot = Join-Path $TempRoot $FixtureName
[IO.Directory]::CreateDirectory($FixtureRoot) | Out-Null

try {
    $Archive = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        foreach ($Entry in $Archive.Entries) {
            $TargetPath = [IO.Path]::GetFullPath((Join-Path $FixtureRoot $Entry.FullName.Replace('/', '\')))
            Assert-Preview ($TargetPath.StartsWith($FixtureRoot + '\', [StringComparison]::OrdinalIgnoreCase)) "the ZIP contains an unsafe path."
            if ([string]::IsNullOrEmpty($Entry.Name)) {
                [IO.Directory]::CreateDirectory($TargetPath) | Out-Null
                continue
            }
            [IO.Directory]::CreateDirectory((Split-Path -Parent $TargetPath)) | Out-Null
            $Input = $Entry.Open()
            $Output = [IO.File]::Create($TargetPath)
            try { $Input.CopyTo($Output) } finally { $Output.Dispose(); $Input.Dispose() }
        }
    }
    finally {
        $Archive.Dispose()
    }

    $ManifestFiles = @(Get-ChildItem -LiteralPath $FixtureRoot -Filter "PREVIEW-MANIFEST.json" -File -Recurse)
    Assert-Preview ($ManifestFiles.Count -eq 1) "expected exactly one PREVIEW-MANIFEST.json."
    $PreviewRoot = $ManifestFiles[0].Directory
    $Manifest = Get-Content -LiteralPath $ManifestFiles[0].FullName -Raw | ConvertFrom-Json

    Assert-Preview ([int]$Manifest.schemaVersion -eq 1) "unsupported manifest schema."
    Assert-Preview ([string]$Manifest.operatingSystem -eq "windows") "the package is not marked for Windows."
    Assert-Preview ([string]$Manifest.architecture -eq "x64") "the package is not marked for x64."
    Assert-Preview ([int]$Manifest.javaRuntimeMajor -eq 11) "the package does not require Java 11."
    Assert-Preview ([string]$Manifest.mainClass -eq "org.gielinor.profilesync.GielinorProfileSyncPreview") "unexpected preview main class."
    Assert-Preview ([string]$Manifest.runeLiteVersion -match '^\d+\.\d+\.\d+$') "RuneLite is not pinned to an exact release."
    Assert-Preview (($Manifest.clientArguments -join ' ') -eq '--developer-mode --debug --profile gielinor-profile-sync-preview') "unexpected client arguments."

    $LibDirectory = Join-Path $PreviewRoot.FullName "lib"
    $ApplicationJar = Join-Path $LibDirectory ([string]$Manifest.applicationJar.file)
    Assert-Preview (Test-Path -LiteralPath $ApplicationJar -PathType Leaf) "the thin application JAR is missing."
    Assert-Preview ((Get-Item -LiteralPath $ApplicationJar).Length -eq [long]$Manifest.applicationJar.sizeBytes) "the application JAR size does not match the manifest."
    Assert-Preview ((Get-Sha256 -FilePath $ApplicationJar) -eq [string]$Manifest.applicationJar.sha256) "the application JAR hash does not match the manifest."

    $ExpectedJars = @([string]$Manifest.applicationJar.file)
    foreach ($Dependency in @($Manifest.dependencies)) {
        $Coordinate = [string]$Dependency.coordinate
        Assert-Preview ($Coordinate -notmatch '^(?:junit|org\.hamcrest):') "test-only dependency $Coordinate entered the runtime."
        $DependencyJar = Join-Path $LibDirectory ([string]$Dependency.file)
        Assert-Preview (Test-Path -LiteralPath $DependencyJar -PathType Leaf) "dependency $Coordinate is missing."
        Assert-Preview ((Get-Item -LiteralPath $DependencyJar).Length -eq [long]$Dependency.sizeBytes) "dependency $Coordinate has an unexpected size."
        Assert-Preview ((Get-Sha256 -FilePath $DependencyJar) -eq [string]$Dependency.sha256) "dependency $Coordinate has an unexpected hash."
        $ExpectedJars += [string]$Dependency.file

        if ($Dependency.pom) {
            Assert-Preview (Test-Path -LiteralPath (Join-Path $PreviewRoot.FullName ([string]$Dependency.pom)) -PathType Leaf) "the POM for $Coordinate is missing."
        }
        foreach ($LegalFile in @($Dependency.embeddedLegalFiles)) {
            Assert-Preview (Test-Path -LiteralPath (Join-Path $PreviewRoot.FullName ([string]$LegalFile)) -PathType Leaf) "legal material for $Coordinate is missing."
        }
    }

    $ActualJars = @(Get-ChildItem -LiteralPath $LibDirectory -Filter "*.jar" -File | Select-Object -ExpandProperty Name | Sort-Object)
    $ExpectedJars = @($ExpectedJars | Sort-Object)
    Assert-Preview (-not (Compare-Object -ReferenceObject $ExpectedJars -DifferenceObject $ActualJars)) "Preview\lib contains an undeclared or missing JAR."
    Assert-Preview (-not ($ActualJars -match '(?i)junit|hamcrest|natives-linux|natives-macos|windows-(?:x86|arm64)')) "Preview\lib contains a test or wrong-platform JAR."

    $ApplicationArchive = [IO.Compression.ZipFile]::OpenRead($ApplicationJar)
    try {
        $ApplicationEntries = @($ApplicationArchive.Entries | Select-Object -ExpandProperty FullName)
        Assert-Preview ($ApplicationEntries -contains "org/gielinor/profilesync/GielinorProfileSyncPreview.class") "the preview launcher class is missing."
        Assert-Preview ($ApplicationEntries -contains "org/gielinor/profilesync/GielinorProfileSyncPlugin.class") "the plug-in class is missing."
        Assert-Preview (-not ($ApplicationEntries -match 'Test\.class$')) "test classes entered the thin application JAR."
        Assert-Preview (-not ($ApplicationEntries -match '^(?:org/junit|org/hamcrest|net/runelite)/')) "a bundled runtime dependency entered the thin application JAR."
        Assert-Preview ($ApplicationEntries -notcontains "META-INF/services/net.runelite.client.plugins.Plugin") "the forbidden Plugin service descriptor is present."
    }
    finally {
        $ApplicationArchive.Dispose()
    }

    Assert-Preview (Test-Path -LiteralPath (Join-Path $PreviewRoot.FullName "THIRD-PARTY-NOTICES.txt") -PathType Leaf) "third-party notices are missing."
    Assert-Preview (Test-Path -LiteralPath (Join-Path $PreviewRoot.FullName "legal\GIELINOR-PROFILE-SYNC-LICENSE.txt") -PathType Leaf) "the project license is missing."

    Write-Output "Preview distribution verified: $($Manifest.dependencies.Count) pinned dependencies, Java 11, RuneLite $($Manifest.runeLiteVersion)."
}
finally {
    $ResolvedFixture = [IO.Path]::GetFullPath($FixtureRoot)
    if ($ResolvedFixture.StartsWith($TempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $ResolvedFixture) -like 'gielinor-preview-verify-*' -and
        (Test-Path -LiteralPath $ResolvedFixture)) {
        [IO.Directory]::Delete($ResolvedFixture, $true)
    }
}
