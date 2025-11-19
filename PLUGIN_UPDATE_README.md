# プラグイン更新スクリプト

このディレクトリには、プラグインを自動更新するためのスクリプトが含まれています。

## 📁 ファイル構成

- `update_plugins.sh` - Linux/macOS用の更新スクリプト
- `update_plugins.ps1` - Windows用の更新スクリプト（PowerShell）
- `plugin_urls.json` - プラグインのダウンロードURL設定ファイル
- `PLUGIN_UPDATE_README.md` - このファイル

## 🚀 使用方法

### Linux/macOS (Bash)

```bash
# スクリプトに実行権限を付与（初回のみ）
chmod +x update_plugins.sh

# プラグインを更新
./update_plugins.sh
```

### Windows (PowerShell)

```powershell
# プラグインを更新
.\update_plugins.ps1
```

## 📦 更新されるプラグイン

| プラグイン | 説明 |
|------------|------|
| **Geyser-Spigot** | Bedrock Edition プレイヤーがJava Editionサーバーに参加可能 |
| **Floodgate** | Geyserと連携してBedrock Editionプレイヤーの認証を管理 |
| **ViaVersion** | 複数のMinecraftバージョンに対応するプロトコル変換 |
| **ViaBackwards** | 古いバージョンのクライアントが新しいサーバーに接続可能 |
| **ViaRewind** | 新しいバージョンのクライアントが古いサーバーに接続可能 |
| **PatrolSpectatorPlugin** | 配信用パトロールプラグイン（自動ビルド） |

## 🔧 スクリプトの動作

1. **バックアップ作成**: 既存のプラグインを `backup_YYYYMMDD_HHMMSS/` に保存
2. **プラグインダウンロード**: 各プラグインの最新版をダウンロード
3. **PatrolSpectatorPluginビルド**: Mavenで最新版をビルド
4. **結果表示**: 更新されたプラグインの一覧を表示

## ⚙️ 設定の変更

`plugin_urls.json` ファイルを編集することで、ダウンロードURLやバージョンを変更できます。

```json
{
  "plugins": {
    "プラグイン名": {
      "url": "ダウンロードURL",
      "description": "説明"
    }
  }
}
```

## 🛡️ 安全機能

- **自動バックアップ**: 更新前に既存プラグインを自動バックアップ
- **エラーハンドリング**: ダウンロード失敗時の適切な処理
- **ログ出力**: 各ステップの詳細なログ表示

## 📝 注意事項

- スクリプト実行前にサーバーを停止してください
- 更新後はサーバーを再起動して変更を反映してください
- バックアップは手動で削除する必要があります

## 🔄 定期更新の設定

### Linux/macOS (crontab)

```bash
# 毎週日曜日の午前3時に自動更新
0 3 * * 0 /path/to/PatrolSpectatorPlugin/update_plugins.sh
```

### Windows (タスクスケジューラ)

1. タスクスケジューラを開く
2. 基本タスクを作成
3. トリガー: 毎週日曜日 午前3:00
4. 操作: PowerShellスクリプトの実行
5. 引数: `-File "C:\path\to\PatrolSpectatorPlugin\update_plugins.ps1"`

## 🆘 トラブルシューティング

### ダウンロードエラー
- インターネット接続を確認
- URLが正しいか確認
- ファイアウォール設定を確認

### ビルドエラー
- JavaとMavenが正しくインストールされているか確認
- プロジェクトの依存関係を確認

### 権限エラー
- スクリプトに実行権限があるか確認
- プラグインディレクトリへの書き込み権限を確認
