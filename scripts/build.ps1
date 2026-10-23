param([switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    if (Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe') {
        $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    }
    # This Windows host rejects Java UNIX-domain socket connections. A missing
    # socket directory makes the JDK use its TCP loopback fallback for this process.
    $socketPath = Join-Path $env:TEMP ('prezzi-socket-fallback-' + [guid]::NewGuid().ToString('N'))
    $socketOption = '-Djdk.net.unixdomain.tmpdir="' + $socketPath + '"'
    $env:JAVA_TOOL_OPTIONS = ($previousOptions + ' ' + $socketOption).Trim()
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        $tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug')
        if ($DeviceTests) { $tasks += 'connectedDebugAndroidTest' }
        & .\gradlew.bat @tasks --console=plain --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process'
        if ($LASTEXITCODE -ne 0) { throw "Gradle ha restituito il codice $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:JAVA_TOOL_OPTIONS = $previousOptions
}
