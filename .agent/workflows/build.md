---
description: ビルドとバージョン管理のワークフロー
---

# ビルドとバージョン管理のワークフロー

このワークフローは、プラグインをビルドする際の標準手順を定義します。

## 前提条件

- Java 21がインストールされていること
- Mavenがインストールされていること（またはプロジェクト内の`.maven`を使用）

## ビルド前の準備

### 1. 現在のビルドファイルをバックアップ

```powershell
# 現在のバージョンのJARファイルをbackupフォルダにコピー
Copy-Item "target\PatrolSpectatorPlugin-<現在のバージョン>.jar" -Destination "backup\PatrolSpectatorPlugin-<現在のバージョン>.jar" -Force
```

**例:**
```powershell
Copy-Item "target\PatrolSpectatorPlugin-1.8.3.jar" -Destination "backup\PatrolSpectatorPlugin-1.8.3.jar" -Force
```

### 2. バージョンを更新

`pom.xml`の`<version>`タグを更新します。

```xml
<version>1.8.4</version>  <!-- 前のバージョンから0.0.1増やす -->
```

**バージョニングルール:**
- 新機能追加: マイナーバージョンを上げる（例: 1.8.3 → 1.8.4）
- バグ修正のみ: パッチバージョンを上げる（例: 1.8.3 → 1.8.3.1）
- 破壊的変更: メジャーバージョンを上げる（例: 1.8.3 → 2.0.0）

### 3. 変更をコミット

```powershell
# 全ての変更をステージング
git add .

# 機能追加の場合
git commit -m "feat: <機能の説明> (v<新しいバージョン>)

- <変更内容1>
- <変更内容2>
- <変更内容3>"

# バグ修正の場合
git commit -m "fix: <修正内容> (v<新しいバージョン>)"

# バージョンアップのみの場合
git commit -m "chore: bump version to <新しいバージョン>"
```

**コミットメッセージの例:**
```powershell
git commit -m "feat: restore ranking display system (v1.8.4)

- Add PlayerStatsStorage extensions for PK and ender dragon tracking
- Create RankingDisplaySystem for periodic ranking announcements
- Create RankingEventListener to track player kills and dragon kills
- Integrate ranking display with PatrolManager"
```

## ビルド手順

### 4. クリーンビルドを実行

// turbo
```powershell
$env:JAVA_HOME = "c:\Users\gonjy\Projects\Private\PatrolSpectatorPlugin\.jdk\jdk-21.0.2+13"
$env:PATH = "$env:JAVA_HOME\bin;c:\Users\gonjy\Projects\Private\PatrolSpectatorPlugin\.maven\maven\bin;$env:PATH"
mvn clean package -DskipTests
```

### 5. ビルド成功を確認

```powershell
# JARファイルが生成されたことを確認
ls target\PatrolSpectatorPlugin-*.jar
```

**期待される出力:**
```
PatrolSpectatorPlugin-<新しいバージョン>.jar
```

## ビルド後の確認

### 6. 生成されたJARファイルを確認

```powershell
# ファイルサイズを確認（極端に小さい場合はビルドエラーの可能性）
ls target\PatrolSpectatorPlugin-*.jar | Select-Object Name, Length
```

### 7. バックアップフォルダを確認

```powershell
# backupフォルダに古いバージョンが保存されていることを確認
ls backup\PatrolSpectatorPlugin-*.jar
```

## デプロイ

### 8. サーバーにアップロード

1. `target\PatrolSpectatorPlugin-<新しいバージョン>.jar`をサーバーの`plugins`フォルダにアップロード
2. サーバーを再起動
3. 動作確認

## トラブルシューティング

### ビルドエラーが発生した場合

1. エラーメッセージを確認
2. コンパイルエラーがある場合は修正
3. 再度ビルドを実行

### JARファイルが生成されない場合

1. `mvn clean`を実行してtargetフォルダをクリーンアップ
2. 再度ビルドを実行

### バージョンが更新されていない場合

1. `pom.xml`の`<version>`タグを確認
2. 正しいバージョンに更新されているか確認
3. 再度ビルドを実行

## 重要な注意事項

> **必ずバージョンを上げてからビルドすること**
> 
> ビルドする際は必ず`pom.xml`のバージョンを更新してください。
> 同じバージョンで複数のビルドを行うと、どのJARファイルが最新かわからなくなります。

> **ビルド前に必ずバックアップを取ること**
> 
> 現在サーバーで稼働しているバージョンのJARファイルは必ずbackupフォルダに保存してください。
> 問題が発生した場合にロールバックできるようにするためです。

> **細かくコミットすること**
> 
> 機能追加やバグ修正は細かくコミットしてください。
> 1つのコミットには1つの変更内容のみを含めるようにしてください。
