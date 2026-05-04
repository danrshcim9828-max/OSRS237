param(
    [string]$Task = "build"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java is not installed. Install JDK 21 and retry."
}

$javaVersion = & java -version 2>&1 | Select-Object -First 1
Write-Host "Detected Java: $javaVersion"

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Gradle is not installed. Install Gradle 8.7+ and retry."
}

Push-Location "$PSScriptRoot/.."
try {
    & gradle --no-daemon clean $Task
}
finally {
    Pop-Location
}
