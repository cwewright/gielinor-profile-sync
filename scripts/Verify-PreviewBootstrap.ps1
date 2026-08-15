[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"

function Assert-Bootstrap {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Preview bootstrap verification failed: $Message" }
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
Assert-Bootstrap ($ArchivePath.EndsWith(".zip", [StringComparison]::OrdinalIgnoreCase)) "the artifact is not a ZIP file."

$TempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$FixtureRoot = Join-Path $TempRoot ("gielinor-bootstrap-verify-" + [Guid]::NewGuid().ToString("N"))
[IO.Directory]::CreateDirectory($FixtureRoot) | Out-Null

try {
    $Archive = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        foreach ($Entry in $Archive.Entries) {
            $TargetPath = [IO.Path]::GetFullPath((Join-Path $FixtureRoot $Entry.FullName.Replace('/', '\')))
            Assert-Bootstrap ($TargetPath.StartsWith($FixtureRoot + '\', [StringComparison]::OrdinalIgnoreCase)) "the ZIP contains an unsafe path."
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
    Assert-Bootstrap ($ManifestFiles.Count -eq 1) "expected exactly one PREVIEW-MANIFEST.json."
    $PreviewRoot = $ManifestFiles[0].Directory
    $Manifest = Get-Content -LiteralPath $ManifestFiles[0].FullName -Raw | ConvertFrom-Json

    Assert-Bootstrap ([int]$Manifest.schemaVersion -eq 1) "unsupported manifest schema."
    Assert-Bootstrap ([string]$Manifest.distributionMode -eq "verified-download") "the package is not a verified-download bootstrap."
    Assert-Bootstrap ([string]$Manifest.operatingSystem -eq "windows") "the package is not marked for Windows."
    Assert-Bootstrap ([string]$Manifest.architecture -eq "x64") "the package is not marked for x64."
    Assert-Bootstrap ([int]$Manifest.javaRuntimeMajor -eq 11) "the package does not require Java 11."
    Assert-Bootstrap ([string]$Manifest.mainClass -eq "org.gielinor.profilesync.GielinorProfileSyncPreview") "unexpected preview main class."
    Assert-Bootstrap ([string]$Manifest.runeLiteVersion -match '^\d+\.\d+\.\d+$') "RuneLite is not pinned to an exact release."
    Assert-Bootstrap (($Manifest.clientArguments -join ' ') -eq '--developer-mode --debug --profile gielinor-profile-sync-preview') "unexpected client arguments."

    $LibDirectory = Join-Path $PreviewRoot.FullName "lib"
    $ApplicationJar = Join-Path $LibDirectory ([string]$Manifest.applicationJar.file)
    Assert-Bootstrap (Test-Path -LiteralPath $ApplicationJar -PathType Leaf) "the thin application JAR is missing."
    Assert-Bootstrap ((Get-Item -LiteralPath $ApplicationJar).Length -eq [long]$Manifest.applicationJar.sizeBytes) "the application JAR size does not match."
    Assert-Bootstrap ((Get-Sha256 -FilePath $ApplicationJar) -eq [string]$Manifest.applicationJar.sha256) "the application JAR hash does not match."

    $ExpectedRelativeFiles = @(
        "lib/$([string]$Manifest.applicationJar.file)",
        "PREVIEW-MANIFEST.json"
    )
    foreach ($SupportFile in @($Manifest.bootstrapFiles)) {
        $RelativePath = ([string]$SupportFile.file).Replace('/', '\')
        Assert-Bootstrap (-not [IO.Path]::IsPathRooted($RelativePath)) "a support-file path is rooted."
        $SupportPath = [IO.Path]::GetFullPath((Join-Path $PreviewRoot.FullName $RelativePath))
        Assert-Bootstrap ($SupportPath.StartsWith($PreviewRoot.FullName + '\', [StringComparison]::OrdinalIgnoreCase)) "a support-file path escapes Preview."
        Assert-Bootstrap (Test-Path -LiteralPath $SupportPath -PathType Leaf) "support file $RelativePath is missing."
        Assert-Bootstrap ((Get-Item -LiteralPath $SupportPath).Length -eq [long]$SupportFile.sizeBytes) "support file $RelativePath has an unexpected size."
        Assert-Bootstrap ((Get-Sha256 -FilePath $SupportPath) -eq [string]$SupportFile.sha256) "support file $RelativePath has an unexpected hash."
        $ExpectedRelativeFiles += ([string]$SupportFile.file).Replace('\', '/')
    }
    Assert-Bootstrap ($Manifest.bootstrapFiles.Count -eq 2) "expected exactly two signed support files."

    $DependencyFiles = @{}
    $DependencyUrls = @{}
    foreach ($Dependency in @($Manifest.dependencies)) {
        $Parts = ([string]$Dependency.coordinate).Split(':')
        Assert-Bootstrap ($Parts.Count -eq 3 -and -not ($Parts -contains '')) "a dependency coordinate is invalid."
        $FileName = [string]$Dependency.file
        Assert-Bootstrap ([IO.Path]::GetFileName($FileName) -eq $FileName) "a dependency file name is unsafe."
        Assert-Bootstrap (-not $DependencyFiles.ContainsKey($FileName)) "dependency file names are not unique."
        $DependencyFiles[$FileName] = $true

        $Repository = if ($Parts[0].StartsWith('net.runelite', [StringComparison]::Ordinal)) {
            'https://repo.runelite.net'
        }
        else {
            'https://repo1.maven.org/maven2'
        }
        $ExpectedUrl = "$Repository/$($Parts[0].Replace('.', '/'))/$($Parts[1])/$($Parts[2])/$FileName"
        Assert-Bootstrap ([string]$Dependency.downloadUrl -ceq $ExpectedUrl) "dependency $($Dependency.coordinate) has an unexpected download URL."
        Assert-Bootstrap (-not $DependencyUrls.ContainsKey($ExpectedUrl)) "dependency download URLs are not unique."
        $DependencyUrls[$ExpectedUrl] = $true
        Assert-Bootstrap ([string]$Dependency.sha256 -match '^[0-9a-f]{64}$') "dependency $($Dependency.coordinate) has an invalid SHA-256."
        Assert-Bootstrap ([long]$Dependency.sizeBytes -gt 0) "dependency $($Dependency.coordinate) has an invalid size."
        Assert-Bootstrap (-not (Test-Path -LiteralPath (Join-Path $LibDirectory $FileName))) "third-party JAR $FileName was redistributed in the bootstrap."
    }
    Assert-Bootstrap ($Manifest.dependencies.Count -gt 20) "the dependency manifest is unexpectedly small."

    $ActualRelativeFiles = @(Get-ChildItem -LiteralPath $PreviewRoot.FullName -File -Recurse | ForEach-Object {
        $_.FullName.Substring($PreviewRoot.FullName.Length + 1).Replace('\', '/')
    } | Sort-Object)
    $ExpectedRelativeFiles = @($ExpectedRelativeFiles | Sort-Object)
    Assert-Bootstrap (-not (Compare-Object -ReferenceObject $ExpectedRelativeFiles -DifferenceObject $ActualRelativeFiles)) "the bootstrap contains an undeclared or missing file."
    Assert-Bootstrap (@($ActualRelativeFiles -match '\.jar$').Count -eq 1) "the bootstrap must contain exactly one JAR."

    $ApplicationArchive = [IO.Compression.ZipFile]::OpenRead($ApplicationJar)
    try {
        $ApplicationEntries = @($ApplicationArchive.Entries | Select-Object -ExpandProperty FullName)
        Assert-Bootstrap ($ApplicationEntries -contains "org/gielinor/profilesync/GielinorProfileSyncPreview.class") "the preview launcher class is missing."
        Assert-Bootstrap ($ApplicationEntries -contains "org/gielinor/profilesync/GielinorProfileSyncPlugin.class") "the plug-in class is missing."
        Assert-Bootstrap (-not ($ApplicationEntries -match 'Test\.class$')) "test classes entered the thin application JAR."
        Assert-Bootstrap (-not ($ApplicationEntries -match '^(?:org/junit|org/hamcrest|net/runelite)/')) "a runtime dependency entered the thin application JAR."
    }
    finally {
        $ApplicationArchive.Dispose()
    }

    Write-Output "Preview bootstrap verified: one application JAR, $($Manifest.dependencies.Count) exact HTTPS downloads, Java 11, RuneLite $($Manifest.runeLiteVersion)."
}
finally {
    $ResolvedFixture = [IO.Path]::GetFullPath($FixtureRoot)
    if ($ResolvedFixture.StartsWith($TempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $ResolvedFixture) -like 'gielinor-bootstrap-verify-*' -and
        (Test-Path -LiteralPath $ResolvedFixture)) {
        [IO.Directory]::Delete($ResolvedFixture, $true)
    }
}
