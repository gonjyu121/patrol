# Plugin Update Script (PowerShell)
# Usage: .\update_plugins.ps1

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot
$targetDir = Join-Path $rootDir "plugins"
# target/ から plugins/ に変更（mvn clean で消えないように）
$configFile = Join-Path $rootDir "plugin_urls.json"


Write-Host "Plugin update script started" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Green

# Load config
if (-not (Test-Path $configFile)) {
    Write-Error "Config file not found: $configFile"
}
$config = Get-Content $configFile -Encoding UTF8 -Raw | ConvertFrom-Json

# Check target dir
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

# Backup existing jars
$existingJars = Get-ChildItem -Path $targetDir -Filter "*.jar"
if ($existingJars.Count -gt 0) {
    $backupDir = Join-Path $rootDir ("backup_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
    Write-Host "Creating backup directory: $backupDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    
    foreach ($jar in $existingJars) {
        Copy-Item $jar.FullName -Destination $backupDir
        Write-Host ("Backed up: " + $jar.Name) -ForegroundColor Gray
    }
}

# Build PatrolSpectatorPlugin
Write-Host "Building PatrolSpectatorPlugin..." -ForegroundColor Yellow
$buildConfig = $config.build_plugin
$buildName = $buildConfig.name

# Read version from pom.xml
$pomPath = Join-Path $rootDir "pom.xml"
$buildVersion = $buildConfig.version

if (Test-Path $pomPath) {
    $match = Select-String -Path $pomPath -Pattern '<version>(.+?)</version>' | Select-Object -First 1
    if ($match) {
        if ($match.Matches.Groups[1].Value) {
            $buildVersion = $match.Matches.Groups[1].Value
            Write-Host ("Found version " + $buildVersion + " from pom.xml") -ForegroundColor Cyan
        }
    }
}

try {
    # Run build
    $cmdArgs = "/c build_jdk21.bat"
    Write-Host ("Executing: cmd " + $cmdArgs) -ForegroundColor Gray
    
    $process = Start-Process -FilePath "cmd" -ArgumentList $cmdArgs -NoNewWindow -Wait -PassThru

    if ($process.ExitCode -eq 0) {
        $builtJar = Join-Path $targetDir ($buildName + "-" + $buildVersion + ".jar")
        if (Test-Path $builtJar) {
            Write-Host ("Build complete: " + $builtJar) -ForegroundColor Green
        }
        else {
            Write-Host ("Build succeeded but file not found: " + $builtJar) -ForegroundColor Yellow
        }
    }
    else {
        Write-Host ("Build failed for " + $buildName) -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host ("Error during build: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

# Download external plugins
Write-Host "Downloading external plugins..." -ForegroundColor Yellow
$plugins = $config.plugins

foreach ($prop in $plugins.PSObject.Properties) {
    $name = $prop.Name
    $pluginInfo = $prop.Value
    $url = $pluginInfo.url
    $description = $pluginInfo.description
    $filename = if ($name.EndsWith(".jar")) { $name } else { $name + ".jar" }
    $outputPath = Join-Path $targetDir $filename

    Write-Host ("Downloading " + $name + " (" + $description + ")...") -ForegroundColor Cyan

    try {
        if ($url -like "MODRINTH:*") {
            $slug = $url -replace "MODRINTH:", ""
            Write-Host ("Searching Modrinth for latest version (" + $slug + ")...") -ForegroundColor Gray
            $apiUrl = "https://api.modrinth.com/v2/project/" + $slug + "/version"
            $versions = Invoke-RestMethod -Uri $apiUrl -Method Get
            $latestVersion = $versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
            if (-not $latestVersion) {
                $latestVersion = $versions | Select-Object -First 1
            }
            
            if ($latestVersion) {
                $downloadUrl = $latestVersion.files[0].url
                Invoke-WebRequest -Uri $downloadUrl -OutFile $outputPath
                Write-Host ("Download complete: " + $name + " (Version: " + $latestVersion.version_number + ")") -ForegroundColor Green
            }
            else {
                Write-Host ("No version found for " + $name) -ForegroundColor Red
            }
        }
        else {
            Invoke-WebRequest -Uri $url -OutFile $outputPath
            Write-Host ("Download complete: " + $name) -ForegroundColor Green
        }
    }
    catch {
        Write-Host ("Download failed for " + $name + ": " + $_.Exception.Message) -ForegroundColor Red
    }
}

# Show results
Write-Host ""
Write-Host "Update results (Target folder):" -ForegroundColor Green
Write-Host "==============================" -ForegroundColor Green
Get-ChildItem -Path $targetDir -Filter "*.jar" | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 2)
    Write-Host ("- " + $_.Name + " (" + $size + " MB)") -ForegroundColor White
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
Write-Host ("Output directory: " + $targetDir) -ForegroundColor Yellow
