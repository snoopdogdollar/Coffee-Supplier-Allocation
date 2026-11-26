[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Demo,
    [switch]$Check,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH

function Get-JavaToolVersion([string]$Executable, [string]$VersionArgument) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = $VersionArgument
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $toolProcess = New-Object System.Diagnostics.Process
    $toolProcess.StartInfo = $startInfo
    try {
        if (-not $toolProcess.Start()) { throw 'Unable to check the selected Java installation.' }
        $version = $toolProcess.StandardOutput.ReadToEnd()
        $null = $toolProcess.StandardError.ReadToEnd()
        $toolProcess.WaitForExit()
        if ($toolProcess.ExitCode -ne 0) { throw 'Unable to check the selected Java installation.' }
        return $version.Trim()
    } finally { $toolProcess.Dispose() }
}

try {
    if (-not $JavaHome) {
        $jdkParent = Join-Path $env:ProgramFiles 'Java'
        if (Test-Path -LiteralPath $jdkParent) {
            $candidate = Get-ChildItem -LiteralPath $jdkParent -Directory |
                Where-Object { $_.Name -like 'jdk-26*' } | Sort-Object Name -Descending |
                Select-Object -First 1
            if ($candidate) { $JavaHome = $candidate.FullName }
        }
    }
    if ($JavaHome) {
        foreach ($tool in @('java.exe', 'javac.exe')) {
            if (-not (Test-Path -LiteralPath (Join-Path $JavaHome "bin/$tool"))) {
                throw 'JavaHome must point to a complete JDK 26 installation.'
            }
        }
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $oldPath
    }

    $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $javaCommand) {
        throw 'Install JDK 26 and set JAVA_HOME, or pass -JavaHome with its installation directory.'
    }
    $compilerVersion = Get-JavaToolVersion $javacCommand.Source '-version'
    if ($compilerVersion -notmatch '^javac 26(?:\.|\s|$)') {
        throw 'This project requires JDK 26. Check JAVA_HOME or pass -JavaHome.'
    }
    $runtimeVersion = Get-JavaToolVersion $javaCommand.Source '--version'
    if ($runtimeVersion -notmatch '^(?:java|openjdk) 26(?:\.|\s|$)') {
        throw 'The Java runtime must also be JDK 26. Check JAVA_HOME or pass -JavaHome.'
    }
    if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) {
        throw 'Maven is missing from PATH. Install Maven, then reopen the terminal.'
    }
    $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $nodeCommand) {
        throw 'The JavaFX launcher needs Node.js 22.20 or newer for its existing database-settings loader. Install Node or use mvn javafx:run with JDBC variables.'
    }

    $launcherArgs = @('"' + (Join-Path $PSScriptRoot 'scripts/javafx.mjs') + '"')
    if ($Demo) { $launcherArgs += '--demo' }
    if ($Check) { $launcherArgs += '--check' }
    if ($Offline) { $launcherArgs += '--offline' }
    # Keep build output in this terminal while the requested JavaFX window is open.
    $launcherProcess = Start-Process -FilePath $nodeCommand.Source -ArgumentList $launcherArgs -NoNewWindow -Wait -PassThru
    if ($launcherProcess.ExitCode -ne 0) {
        throw "JavaFX launcher exited with code $($launcherProcess.ExitCode). See the message above."
    }
} finally {
    # Leave the caller's shell settings intact when the app closes or launch fails.
    $env:JAVA_HOME = $oldJavaHome
    $env:PATH = $oldPath
}
