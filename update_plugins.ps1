# プラグイン更新スクリプト (PowerShell版)
# 使用方法: .\update_plugins.ps1

Write-Host "🚀 プラグイン更新スクリプト開始" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green

# プラグインディレクトリに移動
$pluginDir = Join-Path $PSScriptRoot "plugins"
Set-Location $pluginDir

# バックアップディレクトリを作成
$backupDir = "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
Write-Host "📁 バックアップディレクトリ作成: $backupDir" -ForegroundColor Yellow
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

# 既存プラグインをバックアップ
Write-Host "💾 既存プラグインをバックアップ中..." -ForegroundColor Yellow
Get-ChildItem -Path "*.jar" | ForEach-Object {
    Copy-Item $_.FullName -Destination $backupDir
    Write-Host "📦 $($_.Name) をバックアップしました" -ForegroundColor Gray
}

# プラグインのダウンロードURL定義
$pluginUrls = @{
    "Geyser-Spigot" = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
    "Floodgate" = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
    "ViaVersion" = "https://github.com/ViaVersion/ViaVersion/releases/download/5.0.0/ViaVersion-5.0.0.jar"
    "ViaBackwards" = "https://github.com/ViaVersion/ViaBackwards/releases/download/4.9.0/ViaBackwards-4.9.0.jar"
    "ViaRewind" = "https://github.com/ViaVersion/ViaRewind/releases/download/2.0.0/ViaRewind-2.0.0.jar"
}

# プラグインをダウンロード
Write-Host "⬇️  プラグインをダウンロード中..." -ForegroundColor Yellow
foreach ($plugin in $pluginUrls.Keys) {
    $url = $pluginUrls[$plugin]
    $filename = "$plugin.jar"
    
    Write-Host "📥 $plugin をダウンロード中..." -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri $url -OutFile $filename -UseBasicParsing
        Write-Host "✅ $plugin ダウンロード完了" -ForegroundColor Green
    }
    catch {
        Write-Host "❌ $plugin のダウンロードに失敗しました: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# PatrolSpectatorPluginをビルド
Write-Host "🔨 PatrolSpectatorPluginをビルド中..." -ForegroundColor Yellow
Set-Location ..
try {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "target/patrol-spectator-plugin-1.5.0.jar" "plugins/"
        Write-Host "✅ PatrolSpectatorPlugin ビルド完了" -ForegroundColor Green
    } else {
        Write-Host "❌ PatrolSpectatorPlugin のビルドに失敗しました" -ForegroundColor Red
    }
}
catch {
    Write-Host "❌ Mavenの実行に失敗しました: $($_.Exception.Message)" -ForegroundColor Red
}

Set-Location $pluginDir

# 結果を表示
Write-Host ""
Write-Host "📊 更新結果:" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green
Get-ChildItem -Path "*.jar" | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 2)
    Write-Host "📦 $($_.Name) ($size MB)" -ForegroundColor White
}

Write-Host ""
Write-Host "🎉 プラグイン更新完了！" -ForegroundColor Green
Write-Host "📁 バックアップ: $backupDir" -ForegroundColor Yellow
Write-Host "💡 サーバーを再起動して更新を反映してください" -ForegroundColor Cyan
