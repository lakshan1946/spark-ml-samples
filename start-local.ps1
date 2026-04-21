$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Invoke-GradleOrThrow([string[]]$arguments) {
    & .\gradlew @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle command failed: .\\gradlew $($arguments -join ' ')"
    }
}

function Remove-IfExists([string]$path) {
    if (Test-Path $path) {
        try {
            Remove-Item -Path $path -Force
        }
        catch {
            throw "Could not remove '$path'. It may be locked by another process. Close any running app using this jar and retry."
        }
    }
}

function Stop-StaleApiJavaProcess() {
    $target = 'api-1.0-SNAPSHOT.jar'
    $javaProcs = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq 'java.exe' -and $_.CommandLine -like "*$target*"
    }

    foreach ($p in $javaProcs) {
        try {
            Write-Host "Stopping stale java process PID $($p.ProcessId) holding $target"
            Stop-Process -Id $p.ProcessId -Force
        }
        catch {
            throw "Could not stop stale java process PID $($p.ProcessId). Close it manually and retry."
        }
    }
}

function Resolve-HadoopWinutils() {
    $candidates = @()

    if ($env:HADOOP_HOME) {
        $candidates += (Join-Path $env:HADOOP_HOME "bin\winutils.exe")
    }

    $candidates += @(
        "C:\hadoop\bin\winutils.exe",
        "C:\winutils\bin\winutils.exe",
        "C:\tools\hadoop\bin\winutils.exe"
    )

    $winutilsPath = $null
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            $winutilsPath = $candidate
            break
        }
    }

    if (-not $winutilsPath) {
        throw @"
Missing winutils.exe. This project uses Spark/Hadoop local file APIs that require winutils on Windows.

Fix:
1) Install winutils.exe for Hadoop 2.7.x
2) Place it at one of these paths:
   - C:\hadoop\bin\winutils.exe
   - C:\winutils\bin\winutils.exe
   - C:\tools\hadoop\bin\winutils.exe
3) Re-run this script
"@
    }

    $hadoopHome = Split-Path -Parent (Split-Path -Parent $winutilsPath)
    $env:HADOOP_HOME = $hadoopHome

    if (-not ($env:PATH -split ';' | Where-Object { $_ -eq (Join-Path $hadoopHome 'bin') })) {
        $env:PATH = "$(Join-Path $hadoopHome 'bin');$env:PATH"
    }

    Write-Host "Using HADOOP_HOME=$hadoopHome"
}

# Ensure UTF-8 source decoding for legacy non-ASCII string literals.
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

# Spark/Hadoop on Windows requires winutils for local filesystem permission checks.
Resolve-HadoopWinutils

# Keep spark.distributed-libraries in a path without spaces to avoid URI issues.
$tmpDir = "C:\tmp"
$distJarTarget = Join-Path $tmpDir "spark-distributed-library-1.0-SNAPSHOT.jar"
$distJarSource = Join-Path $root "spark-distributed-library\build\libs\spark-distributed-library-1.0-SNAPSHOT.jar"

if (!(Test-Path $tmpDir)) {
    New-Item -ItemType Directory -Path $tmpDir | Out-Null
}

Write-Host "Building distributed library jar..."
Invoke-GradleOrThrow @(':spark-distributed-library:jar', '-x', 'test')

if (!(Test-Path $distJarSource)) {
    throw "Missing distributed library jar at $distJarSource"
}

Copy-Item -Path $distJarSource -Destination $distJarTarget -Force

# Build API fat jar so we can avoid bootRun command-length limits on Windows.
Write-Host "Building API jar..."
$apiBuildLibs = Join-Path $root "api\build\libs"
$apiJar = Join-Path $apiBuildLibs "api-1.0-SNAPSHOT.jar"
$apiOriginalJar = Join-Path $apiBuildLibs "api-1.0-SNAPSHOT.jar.original"

Stop-StaleApiJavaProcess

# Clear stale outputs before repackaging. This avoids rename collisions.
Remove-IfExists $apiOriginalJar

Invoke-GradleOrThrow @(':api:build', '-x', 'test')

if (!(Test-Path $apiJar)) {
    throw "Missing API jar at $apiJar"
}

# Verify this is a boot-repackaged jar with Main-Class in MANIFEST.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apiJar)
$manifestEntry = $zip.Entries | Where-Object { $_.FullName -ieq "META-INF/MANIFEST.MF" }
if (-not $manifestEntry) {
    $zip.Dispose()
    throw "Built jar has no MANIFEST. Repackage likely failed."
}
$reader = New-Object System.IO.StreamReader($manifestEntry.Open())
$manifest = $reader.ReadToEnd()
$reader.Dispose()
$zip.Dispose()

if ($manifest -notmatch "Main-Class:") {
    throw "Built jar is not executable (missing Main-Class in MANIFEST)."
}

# Runtime property overrides for local machine paths.
$props = @(
    "--spark.distributed-libraries=file:///C:/tmp/spark-distributed-library-1.0-SNAPSHOT.jar",
    "--dou.training.set.csv.file.path=$($root -replace '\\','/')/training-set/dou/2016_may_mini.csv",
    "--dou.regression.model.directory.path=$($root -replace '\\','/')/training-set/dou/regression-model",
    "--dou.clustering.model.directory.path=$($root -replace '\\','/')/training-set/dou/clustering-model",
    "--lyrics.training.set.directory.path=$($root -replace '\\','/')/training-set/lyrics",
    "--lyrics.model.directory.path=$($root -replace '\\','/')/training-set/lyrics/model",
    "--lyrics7.dataset.csv.file.path=$($root -replace '\\','/')/dataset/Merged_dataset.csv",
    "--lyrics7.model.directory.path=$($root -replace '\\','/')/training-set/lyrics/model-8class",
    "--lyrics7.split.seed=42",
    "--mnist.training.set.image.file.path=$($root -replace '\\','/')/training-set/mnist/train-images-idx3-ubyte",
    "--mnist.training.set.label.file.path=$($root -replace '\\','/')/training-set/mnist/train-labels-idx1-ubyte",
    "--mnist.test.set.image.file.path=$($root -replace '\\','/')/training-set/mnist/t10k-images-idx3-ubyte",
    "--mnist.test.set.label.file.path=$($root -replace '\\','/')/training-set/mnist/t10k-labels-idx1-ubyte",
    "--mnist.training.set.parquet.file.path=$($root -replace '\\','/')/training-set/mnist/training-set.parquet",
    "--mnist.test.set.parquet.file.path=$($root -replace '\\','/')/training-set/mnist/test-set.parquet",
    "--mnist.model.directory.path=$($root -replace '\\','/')/training-set/mnist/model",
    "--mnist.validation.set.directory.path=$($root -replace '\\','/')/training-set/mnist/validation-set",
    "--product.training.set.csv.file.path=C:/tmp/product-train.csv",
    "--product.test.set.csv.file.path=C:/tmp/product-test.csv"
)

Write-Host "Starting API on http://localhost:9090 ..."
java "-Dhadoop.home.dir=$env:HADOOP_HOME" -jar $apiJar @props
