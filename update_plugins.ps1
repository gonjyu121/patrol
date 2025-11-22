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
$config = Get-Content $configFile -Raw | ConvertFrom-Json

# targetディレクトリの確認（なければ作成）
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

# バックアップディレクトリを作成（既存のJARがある場合）
$existingJars = Get-ChildItem -Path $targetDir -Filter "*.jar"
if ($existingJars.Count -gt 0) {
    $backupDir = Join-Path $rootDir "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    Write-Host "📁 バックアップディレクトリ作成: $backupDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    
    foreach ($jar in $existingJars) {
        Copy-Item $jar.FullName -Destination $backupDir
        Write-Host "📦 $($jar.Name) をバックアップしました" -ForegroundColor Gray
    }
}

# PatrolSpectatorPluginをビルド
Write-Host "🔨 PatrolSpectatorPluginをビルド中..." -ForegroundColor Yellow
$buildConfig = $config.build_plugin
$buildVersion = $buildConfig.version
$buildName = $buildConfig.name

try {
    # Maven Wrapperがあればそれを使う、なければパスのmvn
    $mvnCmd = "mvn"
    if (Test-Path (Join-Path $rootDir "mvnw.cmd")) {
        $mvnCmd = ".\mvnw.cmd"
    }
    
    # ユーザー環境に合わせて .maven ディレクトリの mvn を優先するロジック（既存スクリプト踏襲）
    $localMvn = Join-Path $rootDir ".maven\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path $localMvn) {
        $mvnCmd = $localMvn
    }
    
    # JDK指定（既存環境踏襲）
    $javaExec = ""
    $localJdk = Join-Path $rootDir ".jdk\jdk-21.0.2+13\bin\javac.exe"
    if (Test-Path $localJdk) {
        $javaExec = "-Dmaven.compiler.fork=true -Dmaven.compiler.executable=$localJdk"
    }

    # コマンド構築 (PowerShellの引数解析を回避するため cmd /c を使用)
    # JDKパスのバックスラッシュをエスケープする必要があるかもしれないが、
    # cmd /c "..." で囲む場合はシングルクォートで囲めば概ね動作する
    
    $cmdArgs = "/c `"$mvnCmd clean package -DskipTests $javaExec`""
    Write-Host "   実行コマンド: cmd $cmdArgs" -ForegroundColor Gray
    
    $process = Start-Process -FilePath "cmd" -ArgumentList $cmdArgs -NoNewWindow -Wait -PassThru

    if ($process.ExitCode -eq 0) {
        # ビルド成果物は既に target にあるはずだが、名前を確認
        $builtJar = Join-Path $targetDir "$buildName-$buildVersion.jar"
        if (Test-Path $builtJar) {
            Write-Host "✅ $buildName ビルド完了 ($builtJar)" -ForegroundColor Green
        }
        else {
            Write-Host "⚠️ ビルドは成功しましたが、ファイルが見つかりません: $builtJar" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "❌ $buildName のビルドに失敗しました" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host "❌ ビルド処理中にエラーが発生しました: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 外部プラグインのダウンロード
Write-Host "⬇️  外部プラグインをダウンロード中..." -ForegroundColor Yellow
$plugins = $config.plugins

foreach ($name in $plugins.PSObject.Properties.Name) {
    $pluginInfo = $plugins.$name
    $url = $pluginInfo.url
    $description = $pluginInfo.description
    $filename = "$name.jar"
    $outputPath = Join-Path $targetDir $filename

    Write-Host "📥 $name ($description) をダウンロード中..." -ForegroundColor Cyan

    try {
        # Modrinth対応
        if ($url -like "MODRINTH:*") {
            $slug = $url -replace "MODRINTH:", ""
            Write-Host "   Modrinth APIから最新バージョンを検索中 ($slug)..." -ForegroundColor Gray
            $apiUrl = "https://api.modrinth.com/v2/project/$slug/version"
            $versions = Invoke-RestMethod -Uri $apiUrl -Method Get
            # 最新の安定版を探す（なければ最新）
            $latestVersion = $versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
            if (-not $latestVersion) {
                $latestVersion = $versions | Select-Object -First 1
            }
            
            if ($latestVersion) {
                $downloadUrl = $latestVersion.files[0].url
                $actualFileName = $latestVersion.files[0].filename
                # ファイル名は指定のもの($name.jar)に統一するか、元ファイル名を使うか。
                # ここでは管理しやすくするため $name.jar にリネームして保存する
                Invoke-WebRequest -Uri $downloadUrl -OutFile $outputPath
                Write-Host "✅ $name ダウンロード完了 (Version: $($latestVersion.version_number))" -ForegroundColor Green
            }
            else {
                Write-Host "❌ $name のバージョンが見つかりませんでした" -ForegroundColor Red
            }
        }
        else {
            # 通常のURL
            Invoke-WebRequest -Uri $url -OutFile $outputPath
            Write-Host "✅ $name ダウンロード完了" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "❌ $name のダウンロードに失敗しました: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 結果を表示
Write-Host ""
Write-Host "📊 更新結果 (Targetフォルダ):" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green
Get-ChildItem -Path $targetDir -Filter "*.jar" | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 2)
    Write-Host "📦 $($_.Name) ($size MB)" -ForegroundColor White
}

Write-Host ""
Write-Host "🎉 処理完了！" -ForegroundColor Green
Write-Host "📁 出力先: $targetDir" -ForegroundColor Yellow
