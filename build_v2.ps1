# Build Script V2 (Clean Version)
# Usage: .\build_v2.ps1

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot
$targetDir = Join-Path $rootDir "target"
$configFile = Join-Path $rootDir "plugin_urls.json"

Write-Host "Build Script Start" -ForegroundColor Green
Write-Host "==================" -ForegroundColor Green

# Load Config
if (-not (Test-Path $configFile)) {
    Write-Error "Config file not found: $configFile"
}
$config = Get-Content $configFile -Raw | ConvertFrom-Json

# Check Target Dir
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

# Backup
$existingJars = Get-ChildItem -Path $targetDir -Filter "*.jar"
if ($existingJars.Count -gt 0) {
    $backupDir = Join-Path $rootDir "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    Write-Host "Backup Directory: $backupDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    
    foreach ($jar in $existingJars) {
        Copy-Item $jar.FullName -Destination $backupDir
        Write-Host "Backed up $($jar.Name)" -ForegroundColor Gray
    }
}

# Build PatrolSpectatorPlugin
Write-Host "Building PatrolSpectatorPlugin..." -ForegroundColor Yellow
$buildConfig = $config.build_plugin
$buildName = $buildConfig.name

# Read Version from pom.xml
$pomPath = Join-Path $rootDir "pom.xml"
$buildVersion = $buildConfig.version

if (Test-Path $pomPath) {
    # Simple regex match using Select-String
    $match = Select-String -Path $pomPath -Pattern '<version>(.+?)</version>' | Select-Object -First 1
    if ($match) {
        if ($match.Matches.Groups[1].Value) {
            $buildVersion = $match.Matches.Groups[1].Value
            Write-Host "Found version $buildVersion in pom.xml" -ForegroundColor Cyan
        }
    }
}

try {
    # Build Command
    $cmdArgs = "/c build_jdk21.bat"
    Write-Host "Running: cmd $cmdArgs" -ForegroundColor Gray
    
    $process = Start-Process -FilePath "cmd" -ArgumentList $cmdArgs -NoNewWindow -Wait -PassThru

    if ($process.ExitCode -eq 0) {
        $builtJar = Join-Path $targetDir "$buildName-$buildVersion.jar"
        if (Test-Path $builtJar) {
            Write-Host "$buildName Build Complete ($builtJar)" -ForegroundColor Green
        }
        else {
            Write-Host "Build success but file not found: $builtJar" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "$buildName Build Failed" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host "Build Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Download External Plugins
Write-Host "Downloading external plugins..." -ForegroundColor Yellow
$plugins = $config.plugins

foreach ($name in $plugins.PSObject.Properties.Name) {
    $pluginInfo = $plugins.$name
    $url = $pluginInfo.url
    $description = $pluginInfo.description
    $filename = "$name.jar"
    $outputPath = Join-Path $targetDir $filename

    Write-Host "Downloading $name..." -ForegroundColor Cyan

    try {
        if ($url -like "MODRINTH:*") {
            $slug = $url -replace "MODRINTH:", ""
            $apiUrl = "https://api.modrinth.com/v2/project/$slug/version"
            
            # Use basic Invoke-RestMethod
            $versions = Invoke-RestMethod -Uri $apiUrl -Method Get
            $latestVersion = $versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
            if (-not $latestVersion) {
                $latestVersion = $versions | Select-Object -First 1
            }
            
            if ($latestVersion) {
                $downloadUrl = $latestVersion.files[0].url
                Invoke-WebRequest -Uri $downloadUrl -OutFile $outputPath
                Write-Host "$name Downloaded" -ForegroundColor Green
            }
            else {
                Write-Host "$name Version Not Found" -ForegroundColor Red
            }
        }
        else {
            Invoke-WebRequest -Uri $url -OutFile $outputPath
            Write-Host "$name Downloaded" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "$name Download Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "Done!" -ForegroundColor Green
Write-Host "Output: $targetDir" -ForegroundColor Yellow
