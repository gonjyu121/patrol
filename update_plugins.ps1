# Plugin Update Script (PowerShell)
# Usage: .\update_plugins.ps1

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot
$targetDir = Join-Path $rootDir "plugins"
# target/ から plugins/ に変更（mvn clean で消えないように）
$configFile = Join-Path $rootDir "plugin_urls.json"

function Test-PluginJar {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -le 0) {
        throw "Downloaded file is empty or missing: $Path"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $descriptor = $archive.Entries | Where-Object {
            $_.FullName -eq "plugin.yml" -or $_.FullName -eq "paper-plugin.yml"
        } | Select-Object -First 1
        if (-not $descriptor) {
            throw "Downloaded JAR is not a Paper/Bukkit plugin: $Path"
        }
    }
    finally {
        $archive.Dispose()
    }
}

function ConvertTo-Version {
    param([Parameter(Mandatory = $true)][string]$Value)
    $numeric = [regex]::Match($Value, '\d+(?:\.\d+){1,3}').Value
    if (-not $numeric) { throw "Version number could not be parsed: $Value" }
    return [version]$numeric
}


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

# 配布対象から外したプラグインが以前の更新結果に残らないよう削除
$obsoleteFiles = @("Floodgate.jar", "GrimAC-Plugin.jar")
foreach ($obsoleteFile in $obsoleteFiles) {
    $obsoletePath = Join-Path $targetDir $obsoleteFile
    if (Test-Path -LiteralPath $obsoletePath) {
        Remove-Item -LiteralPath $obsoletePath -Force
        Write-Host "Removed obsolete plugin: $obsoleteFile" -ForegroundColor DarkGray
    }
}

$plugins = $config.plugins
$downloadFailures = @()

foreach ($prop in $plugins.PSObject.Properties) {
    $name = $prop.Name
    $pluginInfo = $prop.Value
    $url = $pluginInfo.url
    $description = $pluginInfo.description
    $filename = if ($name.EndsWith(".jar")) { $name } else { $name + ".jar" }
    $outputPath = Join-Path $targetDir $filename
    $temporaryPath = $outputPath + ".download"

    Write-Host ("Downloading " + $name + " (" + $description + ")...") -ForegroundColor Cyan

    try {
        if ($url -like "MODRINTH:*") {
            $slug = $url -replace "MODRINTH:", ""
            $loader = if ($pluginInfo.modrinth_loader) { [string]$pluginInfo.modrinth_loader } else { "paper" }
            $loaderQuery = [uri]::EscapeDataString((@($loader) | ConvertTo-Json -Compress))
            Write-Host ("Searching Modrinth for latest stable " + $loader + " version (" + $slug + ")...") -ForegroundColor Gray
            $apiUrl = "https://api.modrinth.com/v2/project/" + $slug + "/version?loaders=" + $loaderQuery
            $versions = Invoke-RestMethod -Uri $apiUrl -Method Get -Headers @{ "User-Agent" = "PatrolSpectatorPlugin-updater/$buildVersion" }
            $latestVersion = $versions |
                Where-Object { $_.version_type -eq "release" -and $_.loaders -contains $loader } |
                Sort-Object { [datetimeoffset]$_.date_published } -Descending |
                Select-Object -First 1

            if (-not $latestVersion) { throw "No stable $loader version found for $name" }
            if ($pluginInfo.min_version -and
                    (ConvertTo-Version $latestVersion.version_number) -lt (ConvertTo-Version ([string]$pluginInfo.min_version))) {
                throw "$name resolved to $($latestVersion.version_number), below required $($pluginInfo.min_version)"
            }

            $downloadFile = $latestVersion.files |
                Where-Object { $_.primary -and $_.filename -like "*.jar" } |
                Select-Object -First 1
            if (-not $downloadFile) {
                $downloadFile = $latestVersion.files | Where-Object {
                    $_.filename -like "*.jar" -and $_.filename -notmatch '(sources|javadoc|dev)'
                } | Select-Object -First 1
            }
            if (-not $downloadFile) { throw "No deployable JAR found for $name $($latestVersion.version_number)" }
            if ($pluginInfo.file_name_pattern -and $downloadFile.filename -notlike ([string]$pluginInfo.file_name_pattern)) {
                throw "Unexpected file selected for ${name}: $($downloadFile.filename)"
            }

            Invoke-WebRequest -Uri $downloadFile.url -OutFile $temporaryPath
            Test-PluginJar -Path $temporaryPath
            Move-Item -LiteralPath $temporaryPath -Destination $outputPath -Force
            Write-Host ("Download complete: " + $name + " (Version: " + $latestVersion.version_number + ", File: " + $downloadFile.filename + ")") -ForegroundColor Green
        }
        else {
            Invoke-WebRequest -Uri $url -OutFile $temporaryPath
            Test-PluginJar -Path $temporaryPath
            Move-Item -LiteralPath $temporaryPath -Destination $outputPath -Force
            Write-Host ("Download complete: " + $name) -ForegroundColor Green
        }
    }
    catch {
        if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath -Force }
        $downloadFailures += $name
        Write-Host ("Download failed for " + $name + ": " + $_.Exception.Message) -ForegroundColor Red
    }
}

if ($downloadFailures.Count -gt 0) {
    throw "Plugin update aborted because download or validation failed: $($downloadFailures -join ', ')"
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
