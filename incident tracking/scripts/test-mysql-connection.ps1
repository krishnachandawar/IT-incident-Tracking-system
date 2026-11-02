$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$libJar = Join-Path $projectRoot "lib\mysql-connector-j-9.2.0.jar"
$outDir = Join-Path $projectRoot "build\classes"

if (-not (Test-Path $libJar)) {
    Write-Error "Missing Connector/J JAR: $libJar"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$sources = Get-ChildItem -Path (Join-Path $projectRoot "src") -Filter *.java -Recurse |
    ForEach-Object { $_.FullName }

Write-Host "Compiling $($sources.Count) Java files..."
& javac -encoding UTF-8 -cp $libJar -d $outDir $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Copy-Item (Join-Path $projectRoot "src\db.properties") (Join-Path $outDir "db.properties") -Force

Write-Host "Running access.MysqlConnectionTest..."
Set-Location $projectRoot
& java -cp "$outDir;$libJar" access.MysqlConnectionTest
exit $LASTEXITCODE
