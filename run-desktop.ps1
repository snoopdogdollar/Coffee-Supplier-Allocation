param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'

# Prefer a real JDK directory; the Oracle javapath shim may launch a different Java.
if (-not $JavaHome) {
    $jdkParent = Join-Path $env:ProgramFiles 'Java'
    if (Test-Path -LiteralPath $jdkParent) {
        $candidates = @(Get-ChildItem -LiteralPath $jdkParent -Directory |
            Where-Object { $_.Name -like 'jdk-26*' } | Sort-Object Name -Descending)
        if ($candidates.Count -gt 0) { $JavaHome = $candidates[0].FullName }
    }
}
if ($JavaHome) {
    $java = Join-Path $JavaHome 'bin/java.exe'
    $javac = Join-Path $JavaHome 'bin/javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) {
        throw 'JavaHome must point to an installed JDK 26.'
    }
} else {
    $java = (Get-Command java -ErrorAction Stop).Source
    $javac = (Get-Command javac -ErrorAction Stop).Source
}

foreach ($setting in @('DB_URL', 'DB_USER', 'DB_PASSWORD')) {
    if ($null -eq [Environment]::GetEnvironmentVariable($setting)) {
        throw "Missing $setting. Set the same database environment as the console application before launching."
    }
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml')
$driverVersion = ($pom.project.dependencies.dependency |
    Where-Object { $_.groupId -eq 'org.postgresql' -and $_.artifactId -eq 'postgresql' }).version
$driver = Join-Path $env:USERPROFILE ".m2/repository/org/postgresql/postgresql/$driverVersion/postgresql-$driverVersion.jar"
if (-not (Test-Path -LiteralPath $driver)) {
    throw 'The PostgreSQL JDBC driver is missing from the Maven cache. Run mvn compile once, then launch again.'
}

$classes = Join-Path $PSScriptRoot 'target/desktop-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sourceList = Join-Path $PSScriptRoot 'target/desktop-sources.txt'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java/com/coffeeallocation') -Filter '*.java' -Recurse)
$lines = @($sources | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines($sourceList, $lines, [Text.UTF8Encoding]::new($false))
& $javac '-encoding' 'UTF-8' '--release' '26' '-d' $classes ('@' + $sourceList)
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& $java '-cp' ($classes + [IO.Path]::PathSeparator + $driver) 'com.coffeeallocation.DesktopMain'
if ($LASTEXITCODE -ne 0) { throw "Desktop app exited with code $LASTEXITCODE." }
