# PatrolSpectatorPlugin Development Log

## Current Status (2026-09-16)
- **Version**: 1.9.109
- **Branch**: `fix/dungeon-entrance-camera-v1.9.109`
- **Issue**: 観光地巡りで死の迷宮入口ではなく地上面が映る問題
- **Changes**:
    - 観光カメラの足元座標を通路床付近へ下げ、目線が迷宮天井を突き抜けないよう修正。
    - カメラ位置から入口内部まで3ブロック高の進入路と床を自動補修。
    - 入口生成時と起動時登録で同じカメラ座標計算を共有。
    - オーバーワールド全域のエルダーガーディアンへ割り込む追跡処理を削除。
    - OtouGame参加時の自動開始間隔を独立設定にし、既定値を10秒へ変更。
    - `/patrol start <秒>` と自動開始で指定された間隔を巡回処理へ正しく反映。

## Current Status (2026-09-13)
- **Version**: 1.9.108
- **Branch**: `fix/dungeon-softlock-reset-v1.9.108`
- **Issue**: 死の迷宮での窒息・通路封鎖・攻略後再構築の安定化
- **Changes**:
    - 通路を3ブロック高にし、罠Mobを安全な空間だけに出現させて一時Mobを自動撤去。
    - 壁内テレポートと岩盤内への落とし穴を廃止し、脱出不能な窒息を防止。
    - ボス討伐時の未構築状態を永続化し、失敗時は次回起動で再試行。
    - 重複構築を防止し、管理者向け `/dungeon reset` を追加。
    - 生成済みの旧迷宮でも起動時に正面入口を補修し、観光巡りへ現在座標で必ず再登録。

## Current Status (2026-09-13)
- **Version**: 1.9.107
- **Branch**: `fix/prevent-live-dragon-reset-v1.9.107`
- **Issue**: 生存中のエンダードラゴンがいる状態での誤リセット・複数スポーン防止
- **Changes**:
    - 自動リセット予約を実際のエンダードラゴン死亡イベントに限定。
    - 別個体の生存、ドラゴン再出現、起動時の予約復元、実行直前に再検証して自動予約を解除。
    - 原因情報のない旧バージョン由来の予約を起動時に破棄。
    - 手動リセットは生存ドラゴンがいても従来どおり明示実行可能。

## Current Status (2026-09-09)
- **Version**: 1.9.106
- **Branch**: `feature/provider-neutral-sftp-deploy-v1.9.106`
- **Issue**: ホスティング事業者を変更可能な安全なデプロイ機構
- **Changes**:
    - 接続先をローカル設定へ分離したOpenSSH SFTPデプロイスクリプトを追加。
    - サーバー停止確認、リモート旧JARの日時付き退避、一時名アップロード、最終名への切り替えを実装。
    - SSH鍵とknown_hostsを必須にし、接続情報をGit・ログ・コマンドラインへ露出させない構成に変更。
    - `Transport` 設定を導入し、将来の別ホスティング事業者・転送方式追加に備えた。

## Current Status (2026-09-06)
- **Version**: 1.9.105
- **Branch**: `fix/hide-patrol-coordinates-v1.9.105`
- **Issue**: 配信画面へのPatrol帰還地点の座標漏えい防止
- **Changes**:
    - `/patrol sethome <1|2>` の完了メッセージからワールド名とXYZ座標を削除。
    - `/patrol homes` は座標を表示せず、各枠の登録済み・未登録のみを表示。
    - `/patrol status` と `/patrol where` は開始地点の有無だけを表示。
    - `/patrol tpback` の成功メッセージからワールド名とXYZ座標を削除。

## Current Status (2026-09-04)
- **Version**: 1.9.104
- **Branch**: `feature/saved-patrol-homes`
- **Issue**: パトロール停止時の帰還安定化と常設帰還地点2枠
- **Changes**:
    - `/patrol stop` と `/patrol back` が復帰先チャンクをロードし、テレポート成功時だけ一時状態を削除するよう改善。
    - 別のカメラ役の古い復帰状態を誤用しないようUUIDを検証。
    - プレイヤーごとに2枠を `patrol_homes.yml` へ保存する `/patrol sethome <1|2>` を追加。
    - `/patrol home <1|2>` と `/patrol homes` を追加。
    - `PatrolHomeStorageTest` で2枠の独立保存と範囲外スロット拒否をテスト。

## Current Status (2026-08-31)
- **Version**: 1.9.102
- **Branch**: `feature/patrol-logic-update`
- **Build Status**: Passed (`mvn clean package` with JDK 21)
- **External Plugins**: Updated all dependencies to latest versions via `build_v2.ps1` (Removed duplicate `Floodgate.jar`, keeping `Floodgate-Spigot.jar`)
- **Key Features & Fixes in v1.9.102**:
    - **Multi-Perspective Dynamic Player Spectating Sequence**:
        - Implemented 3-stage dynamic camera perspective transitions during player patrolling:
          1. **Third-Person Front View (35%)**: Positions camera in front of player facing their face/skin right as the Title "◯◯ さんの視点 (Now On Air)" appears.
          2. **Third-Person Back View (35%)**: Transitions to follow camera behind/above player showing movement and surroundings.
          3. **First-Person View (30%)**: Transitions to natural player first-person spectator view (`setSpectatorTarget(player)`).
        - Smooth real-time position and angle tracking via lightweight invisible armor stand.
        - Solid block occlusion detection to prevent camera clipping inside blocks.
        - Clean cancellation & task cleanup on patrol switch, entity death, world change, or spectator stop.
    - **External Plugin Duplicate Fix**:
        - Removed legacy `Floodgate.jar` from build/plugins pipeline to eliminate duplicate collision with `Floodgate-Spigot.jar`.
- **Key Features & Fixes in v1.9.101**:
    - **Ender Dragon BossBar (HP Bar) Management**:
        - Fixed missing Ender Dragon BossBar by implementing dedicated `BossBar` lifecycle management in `EndGameManager`.
        - Added periodic 1-second sync task to track Ender Dragon health, mode (Normal vs Hard "Void Dragon"), and dynamically register/unregister End world players.
        - Added instant BossBar health update on `EntityDamageEvent` (damage/hit) and automatic cleanup on `EntityDeathEvent` (kill) or End world reset.
        - Handled player dimension transit & join/quit events (`PlayerChangedWorldEvent`, `PlayerJoinEvent`, `PlayerQuitEvent`, `PlayerTeleportEvent`, `PlayerRespawnEvent`) for real-time BossBar visibility.
        - Integrated BossBar reset & immediate recreation synchronization in `EndResetManager`.
        - Added unit tests in `EndGameManagerTest`.
- **Key Features & Fixes in v1.9.100**:
    - **Ender Dragon Spawning on End Recreation**:
        - Fixed missing Ender Dragon after End world recreation by ensuring `battle.generateEndPortal(false)`, `battle.resetCrystals()`, and `battle.setPreviouslyKilled(false)` are initialized, and explicitly spawning an `EnderDragon` entity (with `CIRCLING` phase) if not present.
        - Fixed `checkDragonAbsence()` to also trigger recreation if no Ender Dragon exists even if `hasBeenPreviouslyKilled()` is false (handling unspawned / corrupted states after grace period).
        - Updated `EndGameManager` to look up the End world dynamically instead of using a hardcoded `"world_the_end"` string.
        - Added `EndResetManagerTest` unit tests.
- **Key Features & Fixes in v1.9.99**:
    - **End World Absence & Expired Timer Immediate Recreation Fix**:
        - Fixed `checkDragonAbsence()` to use `DragonBattle.hasBeenPreviouslyKilled()` and respawn state directly, removing the blocking `players.isEmpty()` check that prevented End detection on empty/startup servers.
        - Added immediate End recreation (10-second warning) when a server starts up with an already-defeated Ender Dragon whose reset delay has elapsed.
        - Fixed scheduled countdown resume on startup if reset timer is within delay window.
    - **End World Recreation & Auto-Reset Fix (from v1.9.98)**:
        - Fixed `unloadWorld` failure by adding a 20-tick delay after player evacuation to allow cross-dimension transitions and chunk ticket clearing.
        - Fixed file deletion to completely clean up `region/`, `entities/`, `poi/`, `data/`, `level.dat`, `level.dat_old`, and `session.lock` (with Windows retry logic).
        - Removed erroneous `battle.initiateRespawn()` call on newly created worlds to ensure natural initial Ender Dragon generation.
    - **`/patrol` Command Access**: Restricted all `/patrol` subcommands and tab completions strictly to OP (`sender.isOp()`).
    - **`/patrol back` (Manual Start Recovery)**: Saves pre-patrol inventory, armor, and location to `last_manual_start_state.yml` and restores upon `/patrol back`.
    - **Dungeon Built Flag**: Added `built: true` in `dungeon_config.yml` to prevent duplicate auto-generation on restarts.
    - **Performance Overrides**: Forced `forceViewDistance` and `forceSimulationDistance` per world on startup.
    - **Legacy Stats Expansion**: Extended legacy importer to include `playerKills` and `eventPoints`.
    - **`/patrol travel` Improvement**: Uses player's current location to find distant villages (3000-8000 blocks away).

## System Notes
- **OneComme Integration**: Discontinued/Not used in production. Chat integration operates via Discord / Minecraft built-in listener.

## Fast Context Restoration Protocol
- Next session entrypoint: Read this file (`DEVELOPMENT_LOG.md`) and run `git status -s` first.
- Avoid large unconstrained `git diff` or full directory scans unless specific details are needed.

