# setup_local_env.ps1
$ErrorActionPreference = "Stop"

$rootDir = $PSScriptRoot
$jdkDir = Join-Path $rootDir ".jdk"
$mvnDir = Join-Path $rootDir ".maven"

# JDK 21 Settings (Windows x64)
$jdkVersion = "21.0.2+13"
$jdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_windows_hotspot_21.0.2_13.zip"
$jdkFolderName = "jdk-21.0.2+13"

# Maven Settings
$mvnVersion = "3.9.6"
$mvnUrl = "https://archive.apache.org/dist/maven/maven-3/$mvnVersion/binaries/apache-maven-$mvnVersion-bin.zip"

# Create directories
New-Item -ItemType Directory -Force -Path $jdkDir | Out-Null
New-Item -ItemType Directory -Force -Path $mvnDir | Out-Null

# Setup JDK
$jdkInstallPath = Join-Path $jdkDir $jdkFolderName
if (-not (Test-Path $jdkInstallPath)) {
    Write-Host "Downloading JDK 21..."
    $jdkZip = Join-Path $jdkDir "jdk.zip"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip
    
    Write-Host "Extracting JDK..."
    Expand-Archive -Path $jdkZip -DestinationPath $jdkDir -Force
    Remove-Item $jdkZip
    Write-Host "JDK setup complete."
} else {
    Write-Host "JDK 21 already exists."
}

# Setup Maven
$mvnInstallPath = Join-Path $mvnDir "apache-maven-$mvnVersion"
if (-not (Test-Path $mvnInstallPath)) {
    Write-Host "Downloading Maven..."
    $mvnZip = Join-Path $mvnDir "maven.zip"
    Invoke-WebRequest -Uri $mvnUrl -OutFile $mvnZip
    
    Write-Host "Extracting Maven..."
    Expand-Archive -Path $mvnZip -DestinationPath $mvnDir -Force
    Remove-Item $mvnZip
    Write-Host "Maven setup complete."
} else {
    Write-Host "Maven already exists."
}

# Output environment variables for usage
$javaBin = Join-Path $jdkInstallPath "bin"
$mvnBin = Join-Path $mvnInstallPath "bin"

Write-Host "Setup finished."
Write-Host "To run tests, use:"
Write-Host "& '$mvnBin\mvn.cmd' test -Dmaven.compiler.fork=true -Dmaven.compiler.executable='$javaBin\javac.exe'"
