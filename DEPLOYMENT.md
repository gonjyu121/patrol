# 安全なプラグインデプロイ

`scripts/deploy.ps1` は、OpenSSH SFTPを利用してPatrolSpectatorPluginをMinecraftサーバーへ配置します。接続先はコードから分離されているため、Falix以外へ移行してもローカル設定の変更だけで対応できます。

## 初回設定

1. `deployment.example.psd1` を `deployment.local.psd1` としてコピーします。
2. 接続先、ポート、ユーザー名、リモートのpluginsディレクトリを入力します。
3. 専用のEd25519 SSH鍵を作成し、ホスティング事業者の管理画面へ公開鍵だけを登録します。
4. 接続先のホスト鍵指紋を管理画面・公式案内と照合し、確認済みの鍵を `KnownHostsPath` のファイルへ登録します。

`deployment.local.psd1`、秘密鍵、ローカルknown_hostsはGit管理されません。パスワード認証は、パスワードが引数・履歴・設定ファイルへ残る危険を避けるため対応していません。

## 事前確認（サーバー変更なし）

```powershell
.\scripts\deploy.ps1 -PlanOnly
```

現在のバージョンに対応する `OUT/PatrolSpectatorPlugin-<version>/` とJARを検証し、デプロイ予定だけを表示します。接続先とユーザー名は表示しません。

## デプロイ

管理画面でMinecraftサーバーを停止し、完全に停止したことを確認してから実行します。

```powershell
.\scripts\deploy.ps1 -ConfirmServerStopped
```

処理内容:

1. リモート上の既存 `PatrolSpectatorPlugin*.jar` を列挙。
2. `plugins/.patrol-backups/<日時>/` へ既存JARを移動。
3. 新しいJARを `.uploading` という一時名で転送。
4. 転送成功後に正式名へリネーム。
5. 配置したJARを一時フォルダへ再取得し、ローカルのSHA-256と照合。

転送またはSHA-256照合に失敗した場合は、新しいファイルを除去して退避済みの旧JARを自動的に元へ戻します。

完了後は管理画面からサーバーを起動し、ログで `PatrolSpectatorPlugin` のバージョンを確認します。デプロイスクリプト自身は、誤操作を防ぐためサーバーの停止・起動を行いません。

## 依存プラグインも配置する場合

`deployment.local.psd1` の `DeployDependencies` を `$true` にします。通常はPatrol本体だけを更新し、依存プラグイン更新時だけ有効にしてください。

## ホスティング事業者を変更する場合

SFTP対応事業者なら `deployment.local.psd1` の接続設定を差し替えます。別のAPIや転送方式が必要な場合は、`Transport` の実装を追加します。ビルドや配布フォルダの構造は変更する必要がありません。
