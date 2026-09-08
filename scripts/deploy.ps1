[CmdletBinding()]
param(
    [string]$ConfigFile = (Join-Path (Split-Path $PSScriptRoot -Parent) "deployment.local.psd1"),
    [string]$PackageDirectory,
    [switch]$ConfirmServerStopped,
    [switch]$PlanOnly,
    [string]$SftpExecutable = "sftp.exe"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path $PSScriptRoot -Parent

function Get-ProjectVersion {
    param([Parameter(Mandatory)][string]$PomPath)

    [xml]$pom = Get-Content -LiteralPath $PomPath -Raw
    $version = [string]$pom.project.version
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "pom.xml からプロジェクトバージョンを取得できません。"
    }
    return $version
}

function Assert-SafeRemotePath {
    param([Parameter(Mandatory)][string]$Path)

    if (-not $Path.StartsWith("/")) {
        throw "RemotePluginsDirectory は絶対パスで指定してください。"
    }
    if ($Path -notmatch '^/[A-Za-z0-9._/-]+$' -or $Path.Contains("..")) {
        throw "RemotePluginsDirectory に安全でない文字または相対参照が含まれています。"
    }
    if ($Path -eq "/") {
        throw "サーバールートをデプロイ先には指定できません。"
    }
}

function Quote-SftpPath {
    param([Parameter(Mandatory)][string]$Path)

    if ($Path.Contains('"')) {
        throw "パスにダブルクォートは使用できません。"
    }
    return '"' + $Path + '"'
}

function Protect-ConnectionText {
    param(
        [AllowEmptyString()][string]$Text,
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][string]$UserName
    )

    $protected = $Text
    foreach ($secretPart in @($UserName + "@" + $HostName, $HostName, $UserName)) {
        if (-not [string]::IsNullOrWhiteSpace($secretPart)) {
            $protected = $protected -replace [regex]::Escape($secretPart), "<connection-redacted>"
        }
    }
    return $protected
}

function Invoke-SftpBatch {
    param(
        [Parameter(Mandatory)][string[]]$Commands,
        [Parameter(Mandatory)][hashtable]$Config,
        [Parameter(Mandatory)][string]$Executable
    )

    $batchFile = Join-Path ([System.IO.Path]::GetTempPath()) ("patrol-deploy-" + [guid]::NewGuid().ToString("N") + ".txt")
    try {
        Set-Content -LiteralPath $batchFile -Value $Commands -Encoding utf8NoBOM
        $arguments = @(
            "-b", $batchFile,
            "-P", [string]$Config.Port,
            "-i", [string]$Config.PrivateKeyPath,
            "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=yes",
            "-o", ("UserKnownHostsFile=" + [string]$Config.KnownHostsPath),
            ([string]$Config.UserName + "@" + [string]$Config.HostName)
        )

        $output = & $Executable @arguments 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
        $safeOutput = Protect-ConnectionText -Text $output -HostName $Config.HostName -UserName $Config.UserName
        if ($exitCode -ne 0) {
            throw "SFTP処理に失敗しました (exit=$exitCode)。`n$safeOutput"
        }
        return $safeOutput
    }
    finally {
        Remove-Item -LiteralPath $batchFile -Force -ErrorAction SilentlyContinue
    }
}

function Get-RemoteJarNames {
    param(
        [Parameter(Mandatory)][hashtable]$Config,
        [Parameter(Mandatory)][string]$Executable
    )

    $remoteDirectory = ([string]$Config.RemotePluginsDirectory).TrimEnd("/")
    $output = Invoke-SftpBatch -Commands @(
        ("-ls -1 " + (Quote-SftpPath ($remoteDirectory + "/*.jar")))
    ) -Config $Config -Executable $Executable

    $names = foreach ($line in ($output -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed -match '(?:^|/)([A-Za-z0-9._-]+\.jar)$') {
            $Matches[1]
        }
    }
    return @($names | Sort-Object -Unique)
}

if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
    throw "ローカル設定がありません。deployment.example.psd1 を deployment.local.psd1 にコピーして設定してください。"
}

$config = Import-PowerShellDataFile -LiteralPath $ConfigFile
$requiredKeys = @("Transport", "ProfileName", "HostName", "Port", "UserName", "RemotePluginsDirectory", "PrivateKeyPath", "KnownHostsPath", "DeployDependencies")
foreach ($key in $requiredKeys) {
    if (-not $config.ContainsKey($key)) {
        throw "デプロイ設定に '$key' がありません。"
    }
}
if ($config.Transport -ne "Sftp") {
    throw "未対応のTransportです。現在利用可能: Sftp"
}
if ([int]$config.Port -lt 1 -or [int]$config.Port -gt 65535) {
    throw "Portは1～65535で指定してください。"
}
Assert-SafeRemotePath -Path $config.RemotePluginsDirectory

$version = Get-ProjectVersion -PomPath (Join-Path $projectRoot "pom.xml")
if ([string]::IsNullOrWhiteSpace($PackageDirectory)) {
    $PackageDirectory = Join-Path $projectRoot ("OUT\PatrolSpectatorPlugin-" + $version)
}
$PackageDirectory = [System.IO.Path]::GetFullPath($PackageDirectory)
if (-not (Test-Path -LiteralPath $PackageDirectory -PathType Container)) {
    throw "配布フォルダがありません: $PackageDirectory"
}

$patrolJars = @(Get-ChildItem -LiteralPath $PackageDirectory -File -Filter "PatrolSpectatorPlugin-$version.jar")
if ($patrolJars.Count -ne 1) {
    throw "配布フォルダには PatrolSpectatorPlugin-$version.jar が1個だけ必要です。"
}
$filesToDeploy = @($patrolJars)
if ([bool]$config.DeployDependencies) {
    $filesToDeploy = @(Get-ChildItem -LiteralPath $PackageDirectory -File -Filter "*.jar")
}
if ($filesToDeploy.Count -eq 0 -or @($filesToDeploy | Where-Object Length -eq 0).Count -gt 0) {
    throw "デプロイ対象に空のJARがあります。"
}

$remoteDirectory = ([string]$config.RemotePluginsDirectory).TrimEnd("/")
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$remoteBackupDirectory = $remoteDirectory + "/.patrol-backups/" + $timestamp

Write-Host ("Deployment profile: " + $config.ProfileName) -ForegroundColor Cyan
Write-Host ("Plugin version: " + $version) -ForegroundColor Cyan
Write-Host ("Package: " + $PackageDirectory) -ForegroundColor Cyan
Write-Host ("Remote backup: " + $remoteBackupDirectory) -ForegroundColor Yellow
Write-Host "Files:" -ForegroundColor Cyan
$localHashes = @{}
$filesToDeploy | ForEach-Object {
    $localHashes[$_.Name] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Write-Host ("- " + $_.Name + " (" + $_.Length + " bytes, SHA-256 " + $localHashes[$_.Name] + ")")
}

if ($PlanOnly) {
    Write-Host "PLAN ONLY: サーバーには接続せず、変更も行いません。" -ForegroundColor Green
    return
}
if (-not $ConfirmServerStopped) {
    throw "安全のため、サーバー停止後に -ConfirmServerStopped を付けて実行してください。"
}
if (-not (Test-Path -LiteralPath $config.PrivateKeyPath -PathType Leaf)) {
    throw "SSH秘密鍵が見つかりません。"
}
if (-not (Test-Path -LiteralPath $config.KnownHostsPath -PathType Leaf)) {
    throw "known_hostsが見つかりません。接続先の鍵指紋を確認してから登録してください。"
}
if (-not (Get-Command $SftpExecutable -ErrorAction SilentlyContinue)) {
    throw "OpenSSH sftp.exe が見つかりません。"
}

$remoteJarNames = @(Get-RemoteJarNames -Config $config -Executable $SftpExecutable)
$dependencyNames = @($filesToDeploy | Where-Object Name -NotLike "PatrolSpectatorPlugin*.jar" | ForEach-Object Name)
$existingJarsToBackup = @($remoteJarNames | Where-Object {
    $_ -like "PatrolSpectatorPlugin*.jar" -or ($config.DeployDependencies -and $_ -in $dependencyNames)
})
$backupCommands = @(
    ("-mkdir " + (Quote-SftpPath ($remoteDirectory + "/.patrol-backups"))),
    ("mkdir " + (Quote-SftpPath $remoteBackupDirectory))
)
foreach ($name in $existingJarsToBackup) {
    $backupCommands += "rename " + (Quote-SftpPath ($remoteDirectory + "/" + $name)) + " " + (Quote-SftpPath ($remoteBackupDirectory + "/" + $name))
}
Invoke-SftpBatch -Commands $backupCommands -Config $config -Executable $SftpExecutable | Out-Null

$uploadCommands = @()
foreach ($file in $filesToDeploy) {
    $remoteName = if ($file.Name -like "PatrolSpectatorPlugin*.jar") { "PatrolSpectatorPlugin-$version.jar" } else { $file.Name }
    $temporaryRemotePath = $remoteDirectory + "/." + $remoteName + ".uploading"
    $finalRemotePath = $remoteDirectory + "/" + $remoteName
    $uploadCommands += "put " + (Quote-SftpPath $file.FullName) + " " + (Quote-SftpPath $temporaryRemotePath)
    $uploadCommands += "rename " + (Quote-SftpPath $temporaryRemotePath) + " " + (Quote-SftpPath $finalRemotePath)
}
try {
    Invoke-SftpBatch -Commands $uploadCommands -Config $config -Executable $SftpExecutable | Out-Null

    $verificationDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("patrol-deploy-verify-" + [guid]::NewGuid().ToString("N"))
    try {
        New-Item -ItemType Directory -Path $verificationDirectory -Force | Out-Null
        $verifyCommands = @()
        foreach ($file in $filesToDeploy) {
            $remoteName = if ($file.Name -like "PatrolSpectatorPlugin*.jar") { "PatrolSpectatorPlugin-$version.jar" } else { $file.Name }
            $verifyCommands += "get " + (Quote-SftpPath ($remoteDirectory + "/" + $remoteName)) + " " + (Quote-SftpPath (Join-Path $verificationDirectory $file.Name))
        }
        Invoke-SftpBatch -Commands $verifyCommands -Config $config -Executable $SftpExecutable | Out-Null

        foreach ($file in $filesToDeploy) {
            $downloadedPath = Join-Path $verificationDirectory $file.Name
            $remoteHash = (Get-FileHash -LiteralPath $downloadedPath -Algorithm SHA256).Hash
            if ($remoteHash -ne $localHashes[$file.Name]) {
                throw ("転送後のSHA-256が一致しません: " + $file.Name)
            }
        }
    }
    finally {
        Remove-Item -LiteralPath $verificationDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}
catch {
    $deploymentError = $_
    $rollbackCommands = @()
    foreach ($file in $filesToDeploy) {
        $remoteName = if ($file.Name -like "PatrolSpectatorPlugin*.jar") { "PatrolSpectatorPlugin-$version.jar" } else { $file.Name }
        $rollbackCommands += "-rm " + (Quote-SftpPath ($remoteDirectory + "/." + $remoteName + ".uploading"))
        $rollbackCommands += "-rm " + (Quote-SftpPath ($remoteDirectory + "/" + $remoteName))
    }
    foreach ($name in $existingJarsToBackup) {
        $rollbackCommands += "rename " + (Quote-SftpPath ($remoteBackupDirectory + "/" + $name)) + " " + (Quote-SftpPath ($remoteDirectory + "/" + $name))
    }

    try {
        Invoke-SftpBatch -Commands $rollbackCommands -Config $config -Executable $SftpExecutable | Out-Null
    }
    catch {
        throw ("デプロイ失敗後の自動ロールバックにも失敗しました。サーバーは停止したまま確認してください。原因: " + $deploymentError.Exception.Message)
    }
    throw ("デプロイに失敗したため、旧JARへロールバックしました。原因: " + $deploymentError.Exception.Message)
}

Write-Host "SFTPデプロイとSHA-256照合が完了しました。サーバーを起動し、起動ログでバージョンを確認してください。" -ForegroundColor Green
Write-Host ("Server-side backup: " + $remoteBackupDirectory) -ForegroundColor Yellow
