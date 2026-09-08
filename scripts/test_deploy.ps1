$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path $PSScriptRoot -Parent
$deployScript = Join-Path $PSScriptRoot "deploy.ps1"
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($deployScript, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
    throw ("deploy.ps1 の構文エラー:`n" + ($errors | Out-String))
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("patrol-deploy-test-" + [guid]::NewGuid().ToString("N"))
try {
    $packageDirectory = Join-Path $temporaryRoot "package"
    New-Item -ItemType Directory -Path $packageDirectory -Force | Out-Null

    [xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot "pom.xml") -Raw
    $version = [string]$pom.project.version
    $jarPath = Join-Path $packageDirectory ("PatrolSpectatorPlugin-" + $version + ".jar")
    [System.IO.File]::WriteAllBytes($jarPath, [byte[]](1, 2, 3, 4))

    $configPath = Join-Path $temporaryRoot "deployment.local.psd1"
    $config = @'
@{
    Transport = "Sftp"
    ProfileName = "test"
    HostName = "test.invalid"
    Port = 22
    UserName = "test-user"
    RemotePluginsDirectory = "/plugins"
    PrivateKeyPath = "unused-in-plan-only"
    KnownHostsPath = "unused-in-plan-only"
    DeployDependencies = $false
}
'@
    Set-Content -LiteralPath $configPath -Value $config -Encoding utf8NoBOM

    $output = & $deployScript -ConfigFile $configPath -PackageDirectory $packageDirectory -PlanOnly *>&1 | Out-String
    if ($output -notmatch "PLAN ONLY" -or $output -notmatch [regex]::Escape("PatrolSpectatorPlugin-$version.jar")) {
        throw "PlanOnlyの出力に必要な情報がありません。"
    }
    if ($output -match "test\.invalid" -or $output -match "test-user") {
        throw "接続情報がPlanOnly出力へ露出しています。"
    }

    Write-Host "deploy.ps1 tests passed." -ForegroundColor Green
}
finally {
    Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
}
