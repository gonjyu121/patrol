# PatrolSpectatorPlugin Development Log

## Current Status (2026-09-06)
- **Version**: 1.9.105
- **Branch**: `fix/hide-patrol-home-coordinates`
- **Issue**: 配信画面への常設帰還地点の座標漏えい防止
- **Changes**:
    - `/patrol sethome <1|2>` の完了メッセージからワールド名とXYZ座標を削除。
    - `/patrol homes` は座標を表示せず、各枠の登録済み・未登録のみを表示。

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

