# PatrolSpectatorPlugin Development Log

## v1.9.143 - BEチャット用サーバー設定を自動修復

- 起動時と5分間隔で `server.properties` の `enforce-secure-profile=false` を確認・修復。
- 初期リス保護も `spawn-protection=0` へ保ち、稼働中のBukkit設定へ即時反映。
- 初回変更前に `server.properties.patrol-backup` を作成し、一時ファイル経由で安全に置換。
- 稼働中のSecure Profile強制が有効な場合、設定反映にもう一度再起動が必要なことを管理者へ通知。
- 設定ファイルが見つからない場合やバックアップ・書込みに失敗した場合は変更せず、安全にログへ記録。

## v1.9.142 - 依存プラグインの誤取得を防止

- ModrinthからPaper対応の安定版だけを公開日時順で選択。
- primary JARを優先し、`plugin.yml` / `paper-plugin.yml`がないファイルを配布対象から除外。
- WorldEditはBukkit版のファイル名と7.4.5以上を必須化。
- 1件でも取得または検証に失敗した場合、古い配布物のまま成功扱いにせず更新処理を失敗させるよう修正。

## v1.9.141 - 最近の参加地点タイトルの表示切れを修正

- MCIDと「さんが最後にいた場所」をタイトルの上下2行へ分離。
- 長いMCIDに説明文を連結せず、画面幅に余裕を確保。
- 「またの参加をお待ちしています！」は小さなアクションバーで維持。
- 座標を表示しない既存仕様を維持。

## v1.9.140 - BE版OPの帰還地点利用を修正

- Java版カメラ役の巡回中でも、別アカウントで参加したBE版OPが `/patrol sethome` を実行可能に変更。
- `/patrol home` 利用時、実行者がカメラ役本人でない限り配信用パトロールを停止しないよう修正。
- OPまたは `patrol.admin` を持つPatrol管理ユーザー全員で、帰還地点1・2を共有。
- 既存のJava版・BE版名義の保存地点は、初回利用時に管理者共通枠へ移行。
- 座標を表示しない既存仕様を維持。

## v1.9.139 - 最近の参加地点を観光案内へ追加

- 一般参加者のログアウト地点を直近10人・30日間保存し、無人時の観光巡回へ追加。
- カメラ役には「〇〇さんが最後にいた場所」「またの参加をお待ちしています！」と表示。
- カメラ役自身は記録せず、座標をタイトル・チャット・コンソールログへ表示しない。
- オンライン参加者がいる場合は従来どおりリアルタイム観戦を優先。

## v1.9.136 - サーバー／Bedrock接続診断

- OPまたは `patrol.admin` 向けの `/patrol diagnose` を追加。
- Paper、Patrol、Geyser、Floodgate、Geyser UDPポート、接続中Bedrock人数を読み取り専用で診断。
- Geyser/Floodgateは公開APIの任意連携とし、未導入・無効時もPatrol単体で動作。
- 外部connection testは安定した公開APIがないため `NOT AVAILABLE` と表示し、外部通信を実行しない。
- 設定書換え、再起動、reload、kick、Webhook、自動修復は行わない診断専用。

## v1.9.135 - 観戦対象のコンテナ画面閉じ忘れを修正

- 対象プレイヤーの `InventoryView` を追跡し、チェスト等を閉じたらカメラ側も即座に閉じるように修正。
- Inventoryの参照が変わっても、ミラー状態だけ解除されて画面が残ることがないようにした。
- 終了確認を1 tickごとに短縮。

## v1.9.134 - 観戦対象のコンテナ画面をミラー

- 観戦中の参加者がチェスト、樽、かまど、村人取引などのサーバー管理画面を開いた際、カメラ役にも同じ内容を表示。
- カメラ役のクリックとドラッグはキャンセルし、配信操作やAHK入力で中身を変更しない読み取り専用表示に制限。
- 対象が画面を閉じる、ログアウトする、またはカメラが次の対象へ移るとミラー画面を自動終了。
- クライアントだけで開く本人インベントリ・設定・チャット入力はPaper APIから判別できないため対象外。

## v1.9.133 - 参加者のイベント開始通知を小型化

- 一般参加者の画面中央を覆っていた自動イベント開始タイトルを、ホットバー上の小さなアクションバー通知へ変更。
- 配信演出と検証性を維持するため、カメラ役OtouGameには従来の大型タイトルを表示。
- イベント名と制限時間15分は、小型通知でも引き続き確認可能。

## v1.9.132 - 初期リス再生成の定期案内

- 参加者向けの30分定期案内を、配信カメラ役にも表示するよう変更。
- 既存のconfig.ymlを引き継いだサーバーでも、初期スポーン周辺は荒れ方に応じて鯖主が再生成する場合があることを案内。
- 建築を残したい参加者へ、初期スポーンから5チャンク以上離れるよう案内。
- 定期案内はプレイヤーへ直接送信し、DiscordやYouTubeへ転送しない既存仕様を維持。

## v1.9.131 - WorldEdit初期リス再生成の負荷対策

- WorldEditの履歴記録とブロック更新副作用を無効化し、チャンクごとの大量な近隣更新による長時間停止を防止。
- WorldEdit処理中はウォッチドッグを適切に進行させ、誤ったサーバー停止判定を防止。
- 各チャンクの処理間隔を5秒に広げ、無料枠サーバーが処理の合間に通常動作へ戻れるよう調整。
- 5チャンクごと、および完了時に座標を含まない進捗を表示。

## v1.9.130 - WorldEditによる初期リス再生成

- Paper標準の未対応チャンク再生成APIを廃止し、WorldEdit 7.4.5の再生成APIへ移行。
- 死の迷宮と重なるチャンクは引き続き除外し、残りの対象チャンクを自然地形とバイオームへ順次再生成。
- WorldEditが未導入・無効の場合は、他機能を止めずに再生成コマンドだけを安全に拒否。
- WorldEditを配布セットと最新版ダウンロード対象へ追加。

## v1.9.129 - 初期リス再生成のカメラ判定と互換性修正

- 設定されたカメラ役は初期リス再生成のプレイヤー侵入判定から除外し、観光巡回で処理が中断されないよう修正。
- 一般参加者が対象範囲にいる場合の安全中断は維持。
- サーバーがチャンク再生成APIに対応していない場合、例外を繰り返さず一度で安全に中断し、実行者へ理由を通知。

## v1.9.122 - 自動案内をカメラ役だけに表示

- ランキング、イベント、賞金首、エンド、迷宮などの自動一斉案内を、設定上のカメラ役だけへ送信。
- Java版 `OtouGame` とBE版 `.OtouGame` は表示先として区別し、BEで通常プレイ中は自動案内を表示しない。
- コマンド応答、報酬通知、罠や危険の警告など、本人の操作・ゲーム進行に必要な個別通知は維持。

## v1.9.121 - Java版・BE版の帰還地点共有

- Java版名 `OtouGame` とGeyser経由のBE版名 `.OtouGame` を同じ帰還地点所有者として扱う。
- `/patrol sethome`、`/patrol home`、`/patrol homes` の2枠を両アカウントで共有。
- 従来のUUID形式で保存された地点は、所有者本人が最初に読み込んだ際に共有形式へ自動移行。
- プレイヤー向けメッセージには引き続き座標を表示しない。

## v1.9.120 - ローカル秘密情報のビルド注入

- Git管理外の `secrets/discord-secret.properties` をビルド時にJARへ収録できる仕組みを追加。
- 埋め込みWebhook URLをサーバー側 `config.yml` より優先し、JAR配置だけで新規サーバーへ移行可能にした。
- 秘密情報がない場合は従来の `config.yml` 設定へフォールバックし、既存環境との互換性を維持。
- Webhook URL自体の変更・再発行はこのIssueでは実施しない。

## Current Status (2026-09-17)
- **Version**: 1.9.110
- **Branch**: `feature/dungeon-empty-reset-v1.9.110`
- **Issue**: 死の迷宮が無人になった際の攻略状態自動リセット
- **Changes**:
    - 最後の非スペクテイター挑戦者が退出して60秒間無人なら、迷宮全体を安全に再構築。
    - 宝箱、通常敵、ボス、罠のクールダウンをまとめて初期化。
    - 待機中に挑戦者が戻った場合はリセットを取り消し。
    - ボス討伐後の既存10秒リセットと重複しないよう調停。

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

## Current Status (2026-09-17)
- **Version**: 1.9.111
- **Branch**: `feature/dungeon-multifloor-v1.9.111`
- **Issue**: 現在の死の迷宮の真下へ可能な範囲で地下階層を追加
- **Changes**:
    - ワールド最低高度と中心Yから安全な最大階数を自動計算。
    - 全階をtick分割生成し、各階に迷路・宝箱・罠・ボス部屋・階層ボスを配置。
    - ボス討伐で次階の梯子を解放し、最高到達階ランキングを更新。
    - 最下層攻略時のみ全体を再構築し、旧B1構造は起動時に自動移行。
    - 深層ほど通常敵の種類・出現数・体力・攻撃力と、宝箱・ボス報酬が強化される進行度連動を追加。
    - 最下層攻略時の入口自動帰還を維持し、長い帰路を不要にした。
## Current Status (2026-09-18)
- **Version**: 1.9.112
- **Branch**: `fix/show-empty-dungeon-ranking-v1.9.112`
- **Issue**: 迷宮踏破記録が0件でもランキングを告知
- **Changes**:
    - 迷宮踏破ランキングを記録件数にかかわらず定期ランキングへ表示。
    - 0件時は「最初の挑戦者を待っています」と案内し、参加のきっかけを作る。

## Current Status (2026-09-18)
- **Version**: 1.9.113
- **Branch**: `chore/remove-grimac-v1.9.113`
- **Issue**: 非互換のGrimACを配布・連携対象から削除
- **Changes**:
    - `plugin_urls.json` からGrimACを削除し、今後の依存プラグイン更新・配布対象から除外。
    - カメラ役ログイン時の `grim exempt` 自動実行を削除。

## Current Status (2026-09-19)
- **Version**: 1.9.114
- **Branch**: `feature/lightweight-safety-guard-v1.9.114`
- **Issue**: 無法地帯の方針を維持した軽量サーバー保護
- **Changes**:
    - 対象の当たり判定まで10ブロックを超える直接攻撃だけを無効化。
    - 1秒50ブロックを超える直接破壊は、上限超過分だけを無効化。
    - 判定不能時は許可し、OP・クリエイティブ・スペクテイター・免除権限を対象外に設定。
    - BAN・キック・移動補正・飛行判定・装置やエンティティ生成の制限は実装しない。

## Current Status (2026-09-20)
- **Version**: 1.9.115
- **Branch**: `fix/dungeon-away-from-spawn-v1.9.115`
- **Issue**: 新規ワールドの迷宮が初期リスポーン地点に生成される問題
- **Changes**:
    - 新規設定の迷宮中心をワールドスポーンから X/Z 各256ブロック離して自動保存。
    - スポーンに近い既存中心では自動再生成・入口補修を停止。新規自動生成前に既存構造を確認。
    - 既に生成された迷宮のブロックは自動削除・移動しない。
    - 旧初期設定の中心が残っている場合は設定をバックアップして移行し、自然地形を安全スキャンの妨げにしない。

## Current Status (2026-09-20, release correction)
- **Version**: 1.9.116
- **Branch**: `chore/release-dungeon-fix-v1.9.116`
- **Issue**: 迷宮修正版を旧1.9.115と混同しないための再発行
- **Changes**:
    - PR #25 の修正内容は変えず、JARと配布メタデータのバージョンを1.9.116へ更新。

## Current Status (2026-09-21)
- **Version**: 1.9.117
- **Branch**: `feature/dungeon-build-status-v1.9.117`
- **Issue**: 死の迷宮の予定階数と実際の生成状態を管理者が確認できるようにする
- **Changes**:
    - `/dungeon status` に予定階数、生成処理の進行状態、完了記録、各階の主要構造の抜き取り確認を追加。
    - 構造確認は外殻・部屋・宝箱の3点で行い、全ブロック保証とは表示しない。
    - コマンドには座標を表示しない。
    - 5分周期の迷宮踏破ランキング直後にも、確認できた階数と挑戦者募集を座標なしで放送する。生成中・入口未確認時は案内しない。

## Current Status (2026-09-21, boss reward fix)
- **Version**: 1.9.118
- **Branch**: `fix/dungeon-boss-reward-v1.9.118`
- **Issue**: 迷宮上を歩く参加者がボスの誤ドロップ報酬を拾える問題
- **Changes**:
    - 環境ダメージなど、プレイヤー以外の原因で死亡した迷宮ボスは報酬と踏破処理の対象外に変更。
    - ボスと討伐者の双方が迷宮内にいる正規討伐時だけ、報酬・階層解放・ランキング更新を実行。
    - 迷宮ボスの通常ドロップを無効化し、報酬を専用処理へ一本化。

## Current Status (2026-09-21, dungeon champion reward)
- **Version**: 1.9.119
- **Branch**: `feature/dungeon-champion-v1.9.119`
- **Issue**: 死の迷宮完全踏破者の永続特典と迷宮内リスポーン地点の禁止
- **Changes**:
    - 最下層ボスを正規に倒した本人へ、アイテムと経験値を保持する永続キープインベントリ特典を付与。
    - 特典をUUID単位で `dungeon_stats.yml` に保存し、サーバー再起動後も維持。
    - 死の迷宮内をベッド、リスポーンアンカー、コマンド等でリスポーン地点に設定する処理を拒否。
    - プレイヤー向けメッセージには迷宮やリスポーン地点の座標を表示しない。

## Current Status (2026-09-21, spawn-kill rescue)
- **Version**: 1.9.123
- **Branch**: `feature/spawn-kill-rescue-v1.9.123`
- **Issue**: 一般プレイヤーがリスキル地点から離脱できる救済コマンド
- **Changes**:
    - `/patrol rescue` だけをOP権限なしで利用可能にし、ほかのPatrolコマンドは従来どおりOP専用を維持。
    - 死亡後60秒以内に限り、現在地から2,000～5,000ブロック離れた安全な地表へ避難。
    - 対人被弾直後5秒は使用不可、成功後30分のクールダウン、転送後30秒の保護を追加。
    - 保護中に攻撃すると即時解除。スポーン・死の迷宮付近を避け、座標はメッセージやログへ表示しない。

## Current Status (2026-09-22, consent teleport)
- **Version**: 1.9.124
- **Branch**: `feature/consent-teleport-v1.9.124`
- **Issue**: 一般プレイヤー同士の承認制TP
- **Changes**:
    - `/patrol invite <相手>`、`/patrol accept`、`/patrol deny` をOP権限なしで利用可能に追加。
    - 招待は相手の承認を必須とし、60秒で失効。座標をプレイヤー向け表示やログへ記録しない。
    - 対人戦闘終了後10秒、死の迷宮内、配信用カメラ、サバイバル・アドベンチャー以外では利用不可。
    - 招待送信を10秒に1回へ制限し、死亡・ログアウト時には関係する招待を破棄。

## Current Status (2026-09-22, participant guidance)
- **Version**: 1.9.125
- **Branch**: `feature/participant-guidance-v1.9.125`
- **Issue**: 参加者へチャンネル登録と一般コマンドを定期案内
- **Changes**:
    - 配信用カメラを除く参加者へ、30分間隔でチャンネル登録・高評価の案内を表示。
    - `/stats`、`/patrol rescue`、承認制TPの各コマンドを簡潔に案内。
    - Java版カメラ `OtouGame` は除外し、BE版のプレイ用 `.OtouGame` は参加者として案内対象にする。
    - 直接メッセージで送信し、DiscordやYouTubeへは転送しない。

## Current Status (2026-09-22, safe spawn regeneration)
- **Version**: 1.9.126
- **Branch**: `feature/spawn-reset-v1.9.126`
- **Issue**: 荒らされた初期リス周辺を安全に自然地形へ再生成
- **Changes**:
    - OPまたはコンソール専用の `/patrol spawnreset` と60秒以内の `confirm` を追加。
    - 初期リスを中心とする半径4チャンク、合計81チャンクを負荷分散しながら順番に再生成。
    - 対象範囲にプレイヤーがいる場合や死の迷宮と重なる場合は開始を拒否。
    - 実行中にプレイヤーが対象範囲へ入った場合も安全のため中断し、座標は画面やログへ表示しない。

## Current Status (2026-09-22, dungeon-safe spawn regeneration fix)
- **Version**: 1.9.127
- **Branch**: `fix/spawn-reset-dungeon-exclusion-v1.9.127`
- **Issue**: 既存の死の迷宮が初期リス再生成範囲と重なる場合に処理できない問題
- **Changes**:
    - 死の迷宮との重複を理由に全処理を拒否せず、重なるチャンクだけを再生成対象から除外。
    - 迷宮を保護したまま、残りの初期リス周辺チャンクを自然地形へ再生成可能に変更。
    - 確認画面と管理ログには除外数だけを表示し、迷宮や初期リスの座標は表示しない。

## Current Status (2026-09-23, stuck escape)
- **Version**: 1.9.128
- **Branch**: `feature/stuck-escape-v1.9.128`
- **Issue**: 初期リス保護や穴で閉じ込められた一般参加者の脱出手段
- **Changes**:
    - 一般参加者が死亡条件なしで使える `/patrol escape` を追加。
    - 10秒間静止した後、現在地付近の安全な地表へ移動。移動・被弾・攻撃でキャンセル。
    - 対人被弾後30秒は使用不可、成功後30分のクールダウン、死の迷宮内では使用不可。
    - 長距離移動には使えない範囲へ限定し、プレイヤー向け表示とログに座標を残さない。
## Current Status (2026-09-29, Falix allocation diagnostics)
- **Version**: 1.9.137
- **Branch**: `feature/falix-port-diagnostics-v1.9.137`
- **Issue**: Falixの割当UDPポートとGeyser設定ポートを読み取り専用で比較する
- **Changes**:
    - `/patrol diagnose` からFalix公式allocation APIを非同期に照会し、Primaryと明示的に `Geyser` と記された追加ポートを表示。
    - Geyserの公開APIから取得した設定ポートと比較し、一致・不一致・判別不能を明示。
    - APIキーとserver IDはJAR外の環境変数またはサーバー側外部ファイルだけから読み込み、未設定やAPI障害時も診断を継続。
    - Falix設定・Geyser設定の変更、reload、再起動、自動修復は行わない。
## Current Status (2026-10-01, spawn hazard cleanup)
- **Version**: 1.9.138
- **Branch**: `feature/spawn-hazard-cleanup-v1.9.138`
- **Issue**: 初期リス周辺の通常プレイを残し、プレイヤー由来の危険物だけを局所清掃する
- **Changes**:
    - 溶岩設置と追跡可能な流れ、炎、TNT、エンドクリスタルだけを記録。
    - 既定60分の猶予後、対象範囲に一般参加者がいない場合だけ元のブロック状態へ復元。
    - カメラ役は在室判定から除外し、記録は再起動後も維持。
    - 通常の建築・採掘・自然生成物、チャンク全体、死亡数は自動清掃の判定対象にしない。
    - 座標と設置者名は画面・管理ログへ表示しない。
    - 手動の全面再生成開始時は古い追跡記録を破棄し、再生成後の地形へ古い差分を適用しない。
## Current Status (2026-10-10, camera safety during load pause)
- **Version**: 1.9.144
- **Branch**: `fix/camera-death-on-patrol-pause-v1.9.144`
- **Issue**: 高負荷による巡回一時停止時にカメラ役が死亡する可能性を解消
- **Changes**:
    - 高負荷時は通常の巡回終了処理を使わず、カメラ役のSpectator・無敵を維持したまま巡回タスクだけを一時停止。
    - 負荷回復後は開始地点やインベントリを再保存せず、保存済み状態のまま巡回を再開。
    - 通常停止でも、安全な復帰先へテレポートした後にのみSurvival化と無敵解除を実行。
## Current Status (2026-10-10, recent player avatar)
- **Version**: 1.9.145
- **Branch**: `feature/recent-player-avatar-v1.9.145`
- **Issue**: 最近の参加地点に参加者スキンの3Dヘッドを表示
- **Changes**:
    - 参加者の退出時にプレイヤープロフィールをヘッドアイテムとして最近の参加地点データへ保存。
    - 「○○さんが最後にいた場所」の観光案内中だけ、カメラ正面に3Dヘッドを表示し、次の案内前に削除。
    - Java/BEプロフィールからスキンを取得できない場合も、通常のプレイヤーヘッドで案内を継続。
    - プレイヤー座標はタイトル・チャット・管理ログに表示しない。

## Current Status (2026-10-11, camera chunk load reduction)
- **Version**: 1.9.146
- **Branch**: `fix/camera-chunk-load-v1.9.146`
- **Issue**: 観光カメラ移動時のチャンク負荷を軽減
- **Changes**:
    - 観光地の事前読み込みを周囲9チャンクから移動先1チャンクだけに縮小。
    - オンライン参加者の周辺はサーバーが保持するため、Patrolからの重複先読みを廃止。
    - 平均MSPTが45msを超えている間は、カメラと巡回状態を維持したまま次の移動だけを延期。
    - 負荷判定と安全な既定値をテストで固定。

## Current Status (2026-10-11, bundled spark load guard)
- **Version**: 1.9.147
- **Branch**: `fix/spark-world-statistics-v1.9.147`
- **Issue**: Paper内蔵Sparkのワールド統計取得に伴う停止を抑制
- **Changes**:
    - Paper公式の `config/paper-global.yml` にある `spark.enabled` だけを安全に無効化。
    - 設定変更前に `paper-global.yml.patrol-backup` を一度だけ作成し、書き込みは一時ファイルから置換。
    - Spark設定以外のPaper設定とコメントを維持し、内部APIやreflectionには依存しない。
    - 設定ファイルがない環境、既に無効な環境、バックアップ失敗時もプラグイン起動を継続。

## Current Status (2026-10-11, CloudSync authentication retry guard)
- **Version**: 1.9.148
- **Branch**: `fix/cloud-sync-auth-retry-v1.9.148`
- **Issue**: CloudSync認証失敗時の無駄な定期再試行を停止
- **Changes**:
    - GitHub Gist APIがHTTP 401を返した場合、そのサーバー起動中のCloudSync通信を停止。
    - 認証エラーは一度だけ簡潔に記録し、APIレスポンス本文をログへ出さない。
    - 一時的なサーバーエラー等は停止扱いにせず、次回の定期同期で再試行。
    - 設定修正後はサーバー再起動で同期を再開。

## Current Status (2026-10-11, End reset idle log suppression)
- **Version**: 1.9.149
- **Branch**: `fix/end-reset-idle-log-v1.9.149`
- **Issue**: Endリセットの正常待機状態による警告ログ連発を停止
- **Changes**:
    - 読み込み済みエンダードラゴンが見つからないだけの状態では、5分ごとの警告を出さない。
    - ドラゴン死亡イベントなしでは自動リセットしない既存の安全策を維持。
    - 手動リセット、死亡イベント後の予約、複数ドラゴン保護の挙動は変更しない。

## Current Status (2026-10-11, recent player avatar rollback)
- **Version**: 1.9.150
- **Branch**: `revert/recent-player-avatar-v1.9.150`
- **Issue**: 最近の参加地点で判別できない3Dアバター表示を撤回
- **Changes**:
    - 最近の参加地点で生成していたプレイヤーヘッドのItemDisplayを廃止。
    - プレイヤーヘッドの取得・保存処理も削除し、不要なプロフィール処理を停止。
    - MCIDと「さんが最後にいた場所」の2行タイトル、再参加案内、地点巡回は維持。
    - 旧データに残るheadDataは読み込まず、次回保存時に除去。
