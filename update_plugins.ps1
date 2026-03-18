# プラグイン更新スクリプト (PowerShell版)
# 使用方法: .\update_plugins.ps1

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot
$targetDir = Join-Path $rootDir "target"
$configFile = Join-Path $rootDir "plugin_urls.json"

Write-Host "🚀 プラグイン更新スクリプト開始" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green

# 設定ファイルの読み込み
if (-not (Test-Path $configFile)) {
    Write-Error "設定ファイルが見つかりません: $configFile"
}
$config = Get-Content $configFile -Encoding UTF8 -Raw | ConvertFrom-Json

# targetディレクトリの確認（なければ作成）
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

# バックアップディレクトリを作成（既存のJARがある場合）
$existingJars = Get-ChildItem -Path $targetDir -Filter "*.jar"
if ($existingJars.Count -gt 0) {
    $backupDir = Join-Path $rootDir ("backup_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
    Write-Host "📁 バックアップディレクトリ作成: $backupDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    
    foreach ($jar in $existingJars) {
        Copy-Item $jar.FullName -Destination $backupDir
        Write-Host ("📦 " + $jar.Name + " をバックアップしました") -ForegroundColor Gray
    }
}

# PatrolSpectatorPluginをビルド
Write-Host "🔨 PatrolSpectatorPluginをビルド中..." -ForegroundColor Yellow
$buildConfig = $config.build_plugin
$buildName = $buildConfig.name

# pom.xmlからバージョンを読み取る
$pomPath = Join-Path $rootDir "pom.xml"
$buildVersion = $buildConfig.version

if (Test-Path $pomPath) {
    $match = Select-String -Path $pomPath -Pattern '<version>(.+?)</version>' | Select-Object -First 1
    if ($match) {
        if ($match.Matches.Groups[1].Value) {
            $buildVersion = $match.Matches.Groups[1].Value
            Write-Host ("ℹ️ pom.xmlからバージョン " + $buildVersion + " を検出しました") -ForegroundColor Cyan
        }
    }
}

try {
    # ビルド実行
    $cmdArgs = "/c build_jdk21.bat"
    Write-Host ("   実行コマンド: cmd " + $cmdArgs) -ForegroundColor Gray
    
    $process = Start-Process -FilePath "cmd" -ArgumentList $cmdArgs -NoNewWindow -Wait -PassThru

    if ($process.ExitCode -eq 0) {
        $builtJar = Join-Path $targetDir ($buildName + "-" + $buildVersion + ".jar")
        if (Test-Path $builtJar) {
            Write-Host ("✅ " + $buildName + " ビルド完了 (" + $builtJar + ")") -ForegroundColor Green
        }
        else {
            Write-Host ("⚠️ ビルドは成功しましたが、ファイルが見つかりません: " + $builtJar) -ForegroundColor Yellow
        }
    }
    else {
        Write-Host ("❌ " + $buildName + " のビルドに失敗しました") -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host ("❌ ビルド処理中にエラーが発生しました: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

# 外部プラグインのダウンロード
Write-Host "⬇️ 外部プラグインをダウンロード中..." -ForegroundColor Yellow
$plugins = $config.plugins

foreach ($prop in $plugins.PSObject.Properties) {
    $name = $prop.Name
    $pluginInfo = $prop.Value
    $url = $pluginInfo.url
    $description = $pluginInfo.description
    $filename = if ($name.EndsWith(".jar")) { $name } else { $name + ".jar" }
    $outputPath = Join-Path $targetDir $filename

    Write-Host ("📥 " + $name + " (" + $description + ") をダウンロード中...") -ForegroundColor Cyan

    try {
        if ($url -like "MODRINTH:*") {
            $slug = $url -replace "MODRINTH:", ""
            Write-Host ("   Modrinth APIから最新バージョンを検索中 (" + $slug + ")...") -ForegroundColor Gray
            $apiUrl = "https://api.modrinth.com/v2/project/" + $slug + "/version"
            $versions = Invoke-RestMethod -Uri $apiUrl -Method Get
            $latestVersion = $versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
            if (-not $latestVersion) {
                $latestVersion = $versions | Select-Object -First 1
            }
            
            if ($latestVersion) {
                $downloadUrl = $latestVersion.files[0].url
                Invoke-WebRequest -Uri $downloadUrl -OutFile $outputPath
                Write-Host ("✅ " + $name + " ダウンロード完了 (Version: " + $latestVersion.version_number + ")") -ForegroundColor Green
            }
            else {
                Write-Host ("❌ " + $name + " のバージョンが見つかりませんでした") -ForegroundColor Red
            }
        }
        else {
            Invoke-WebRequest -Uri $url -OutFile $outputPath
            Write-Host ("✅ " + $name + " ダウンロード完了") -ForegroundColor Green
        }
    }
    catch {
        Write-Host ("❌ " + $name + " のダウンロードに失敗しました: " + $_.Exception.Message) -ForegroundColor Red
    }
}

# 結果を表示
Write-Host ""
Write-Host "📊 更新結果 (Targetフォルダ):" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green
Get-ChildItem -Path $targetDir -Filter "*.jar" | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 2)
    Write-Host ("📦 " + $_.Name + " (" + $size + " MB)") -ForegroundColor White
}

Write-Host ""
Write-Host "🎉 処理完了！" -ForegroundColor Green
Write-Host ("📁 出力先: " + $targetDir) -ForegroundColor Yellow
