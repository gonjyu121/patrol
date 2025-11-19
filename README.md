# PatrolSpectatorPlugin

A Paper/Spigot plugin for streaming: it cycles your perspective across online players so viewers can see what's happening on the server. Designed for "hands-free" patrol during streams while you are AFK.

---

## 配信コンセプト
**「誰でも参加OK」無法サバイバル鯖｜荒らし・PK歓迎のマイクラ参加型**

### YouTubeタイトル・説明欄
【誰でも参加OK】無法サバイバル鯖｜荒らし・PK歓迎のマイクラ参加型

⚔ 参加型マイクラ 無法地帯サバイバル ⚔  
BE版・JAVA版どちらでも参加可能！荒らし・PVP・PK・略奪・チート なんでもOKのサーバーで自由に遊ぼう！

📌 サーバーアドレス  
otougame.falixsrv.me  

📌 ルール  
・荒らし、PVP、チート自由  
・禁止事項：配信妨害、過度な暴言  
・楽しく自由に！自己防衛必須！

📌 参加方法  
1. Minecraftを起動（BE版 or JAVA版）  
2. マルチプレイからサーバー追加  
3. アドレスに「otougame.falixsrv.me」を入力して接続  

💬 コメント歓迎！初見さんも気軽に参加してね！

### 配信の特徴
- **参加型配信**: 視聴者が実際にサーバーに参加可能  
- **無法地帯**: 荒らし・PVP・PK・略奪・チート自由  
- **双方向性**: コメントとゲーム内アクションの連動  
- **自動化**: 12時間毎の自動配信再開で視聴者維持  

---

# 🧭 概要 / 主機能

Minecraft Paper / Spigot 向けの多機能サーバー運営支援プラグイン。  
**巡回（Patrol）・観戦（Spectator）・参加率向上・自動イベント・エンド自動リセット**などを一体化。

### 構成モジュール
- **Patrol / Spectator 管理**: 観戦モード巡回、対象再構築、アナウンス、Title/ActionBar 表示
- **End リセット**: エンドラ討伐検知→遅延告知→安全退避→再生成まで全自動
- **AFK / Anti-AFK**: 放置検知と静かなアンチAFKアクション
- **保護（Protection）**: 一時保護バブル生成・延長・停止・近隣探索
- **ツーリスト（TouristLocation）**: 観光地（POI）登録/自動検出/テレポ/巡回
- **Engagement / Rank**: 参加時間・回数・PK・討伐・生存時間の統計とランキング、個人/全体報酬
- **AutoEvent**: 1時間ごとランダムイベント（モブハント/採掘/サバイバル/スピード）自動開始・終了・表彰
- **Rules / 初参加ガイド**: 30分ごとのルール表示、同意コマンド
- **ユーティリティ**: ブロードキャスト、自己診断、自己修復、設定監視
- **disableLocatorBar（HUD制御）**: ロケータバーなどの HUD を無効化（荒らし・PKが有利になってしまうので無効）

---

# Commands

- `/spectate` — toggle spectator/survival (OP権限必要)  
- `/patrol` or `/patrol next` — switch to next target (OP権限必要)  
- `/patrol start [seconds]` — start auto-patrol with interval (default from config) (OP権限必要)  
- `/patrol stop` — stop auto-patrol (OP権限必要)  
- `/patrol rebuild` — rebuild target order from current eligible players (OP権限必要)  
- `/patrol list` — show current patrol order (OP権限必要)  
- `/patrol reload` — reload config (OP権限必要)  
- `/patrol dragon <プレイヤー名>` — 手動でエンドラ討伐を記録 (OP権限必要)  
- `/patrol diag` — 現在のモード/対象/観光設定を表示 (OP権限必要)  
- `/patrol resetpoints` — 全プレイヤーのイベントポイントをリセット（配布済み報酬は回収しない）(OP権限必要)  
  - エイリアス: `/patrol reseteventpoints`  
- `/patrol autoevent status|start|stop` — 自動イベント制御  
- `/patrol listlocations|reloadlocations|teleportlocation <番号>` — 観光地関連  

---

# Permissions
- `patrolspectator.use` (default: op) — use commands  
- `patrolspectator.exempt` — excluded from being patrolled  

---

# Configuration (`config.yml`)
```yaml
intervalSeconds: 10
useSpectatorCamera: true
allowedWorlds: []
exemptPermission: patrolspectator.exempt
announceToPlayers: true
useTitle: true
announceFormat: "&7[Stream]&f Now watching: &a%target%"

useArmorStandPOIs: true
armorStandPOITag: patrol_poi
touristLocations:
  - world: world
    x: 0
    y: 120
    z: 0
    yaw: 0
    pitch: 45
    name: "スポーン上空"

disableLocatorBar: true
singleSleepEnabled: true
