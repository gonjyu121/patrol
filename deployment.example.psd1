@{
    # 現在はOpenSSHのSFTPに対応。将来、別方式を追加する際の切替点です。
    Transport = "Sftp"
    ProfileName = "production"

    # 実際の値は deployment.local.psd1 に記載してください。
    # deployment.local.psd1 は .gitignore の対象です。
    HostName = "sftp.example.invalid"
    Port = 22
    UserName = "minecraft-server-user"
    RemotePluginsDirectory = "/plugins"

    # パスワードをファイルや引数へ保存しないため、SSH鍵認証のみを使用します。
    PrivateKeyPath = "C:\\Users\\YOUR_NAME\\.ssh\\patrol_deploy_ed25519"
    KnownHostsPath = "C:\\Users\\YOUR_NAME\\.ssh\\known_hosts"

    # false: Patrol本体だけを更新（通常はこちら）
    # true : 配布フォルダ内の依存プラグインも同時に更新
    DeployDependencies = $false
}
