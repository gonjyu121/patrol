package dev.gonjy.patrolspectator;

import dev.gonjy.patrolspectator.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent; // ← 修正ポイント（entity配下）
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public final class PatrolSpectatorPlugin extends JavaPlugin implements Listener, TabExecutor, TabCompleter {

    // === 権限 ===
    private static final String PERM_USE = "patrolspectator.use";
    private static final String PERM_EXEMPT = "patrolspectator.exempt"; // 巡回対象から除外

    // === パトロール状態 ===
    private UUID controller;                      // /patrol start 実行者（配信者）
    private GameMode controllerOriginalMode;
    private Location controllerOriginalLoc;
    private BukkitTask patrolTask;
    private int periodSeconds = 10;               // デフォルト切替間隔
    private final List<UUID> targets = new ArrayList<>();
    private int targetIndex = -1;

    @Override
    public void onEnable() {
        // Spigot互換ティック取得の初期化
        Ticks.init(this);

        // イベント・コマンド登録
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("patrol")).setExecutor(this);
        Objects.requireNonNull(getCommand("patrol")).setTabCompleter(this);

        getLogger().info("=== PatrolSpectatorPlugin 有効化 ===");
    }

    @Override
    public void onDisable() {
        // パトロール中なら安全に終了
        safeStop(false);
        Ticks.shutdown();
        getLogger().info("=== PatrolSpectatorPlugin 無効化 ===");
    }

    // ===== コマンド実装 =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission(PERM_USE) && !p.isOp()) {
            p.sendMessage("権限がありません。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                int sec = periodSeconds;
                if (args.length >= 2) {
                    try {
                        sec = Math.max(3, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                        p.sendMessage("秒数は整数で指定してください（例: /patrol start 10）");
                        return true;
                    }
                }
                startPatrol(p, sec);
                return true;
            }
            case "stop":
            case "end": {
                if (!isController(p)) {
                    p.sendMessage("あなたは現在の巡回コントローラではありません。");
                    return true;
                }
                safeStop(true);
                return true;
            }
            case "next": {
                if (!isController(p)) {
                    p.sendMessage("あなたは現在の巡回コントローラではありません。");
                    return true;
                }
                goNextTarget(true);
                return true;
            }
            case "reload": {
                rebuildTargets();
                p.sendMessage("巡回対象を再構築しました。対象数: " + targets.size());
                return true;
            }
            case "list": {
                List<String> names = targets.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .map(Player::getName)
                        .collect(Collectors.toList());
                p.sendMessage("現在の巡回対象: " + (names.isEmpty() ? "(なし)" : String.join(", ", names)));
                return true;
            }
            default:
                sendHelp(p);
                return true;
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("/patrol start [秒] - 自動巡回を開始（デフォルト10秒）");
        p.sendMessage("/patrol stop|end     - 自動巡回を停止して元に戻る");
        p.sendMessage("/patrol next         - 次の対象へ切替");
        p.sendMessage("/patrol reload       - 対象を再構築");
        p.sendMessage("/patrol list         - 現在の巡回対象一覧を表示");
    }

    // ===== タブ補完 =====
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "end", "next", "reload", "list")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean isController(Player p) {
        return controller != null && controller.equals(p.getUniqueId());
    }

    // ===== パトロール本体 =====

    private void startPatrol(Player p, int sec) {
        // 既に誰かが巡回中なら止める（単一コントローラ運用）
        if (controller != null && patrolTask != null) {
            safeStop(false);
        }
        this.controller = p.getUniqueId();
        this.controllerOriginalMode = p.getGameMode();
        this.controllerOriginalLoc = p.getLocation().clone();
        this.periodSeconds = sec;

        // 対象を構築
        rebuildTargets();
        if (targets.isEmpty()) {
            p.sendMessage("巡回対象がいません（オンラインの除外されていないプレイヤーが0）。");
        }

        // 観戦化
        setControllerSpectator(true);
        // 最初のターゲットへ
        goNextTarget(false);

        // タイマー開始
        long periodTicks = Math.max(20L * sec, 60L); // 最低3秒相当
        this.patrolTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            // 安全確認
            Player ctrl = Bukkit.getPlayer(controller);
            if (ctrl == null || !ctrl.isOnline()) {
                safeStop(true);
                return;
            }
            // 定期的に対象を見直し（離脱/参加の反映）
            if (Ticks.current() % (20 * 30) == 0) { // 30秒に1回再構築
                rebuildTargets();
            }
            goNextTarget(false);
        }, periodTicks, periodTicks);

        p.sendMessage("🎥 巡回開始: " + sec + "秒ごとに対象を切り替えます。対象数: " + targets.size());
    }

    private void safeStop(boolean announce) {
        if (patrolTask != null) {
            try { patrolTask.cancel(); } catch (Throwable ignored) {}
            patrolTask = null;
        }
        if (controller != null) {
            Player p = Bukkit.getPlayer(controller);
            if (p != null) {
                // 観戦ターゲット解除
                clearSpectatorTarget(p);
                // 元のモード・位置へ
                if (controllerOriginalMode != null) {
                    try { p.setGameMode(controllerOriginalMode); } catch (Throwable ignored) {}
                }
                if (controllerOriginalLoc != null) {
                    try { p.teleport(controllerOriginalLoc); } catch (Throwable ignored) {}
                }
                if (announce) {
                    p.sendMessage("🛑 巡回を停止しました。");
                }
            }
        }
        controller = null;
        controllerOriginalMode = null;
        controllerOriginalLoc = null;
        targetIndex = -1;
        targets.clear();
    }

    private void setControllerSpectator(boolean toSpectator) {
        Player p = (controller == null) ? null : Bukkit.getPlayer(controller);
        if (p == null) return;
        if (toSpectator) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SPECTATOR);
            }
        } else {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void clearSpectatorTarget(Player p) {
        try {
            // Spectator の視点ターゲットを解除
            p.setSpectatorTarget(null);
        } catch (Throwable ignored) {
            // 古いAPIでも失敗しないよう握りつぶす
        }
    }

    private void setSpectatorTarget(Player p, Entity target) {
        try {
            p.setSpectatorTarget(target); // これが配信の“視点切替”
        } catch (Throwable ignored) {
            // もし環境依存で失敗した場合はテレポートにフォールバック
            try { p.teleport(target.getLocation()); } catch (Throwable ignored2) {}
        }
    }

    private void goNextTarget(boolean manualAnnounce) {
        Player ctrl = (controller == null) ? null : Bukkit.getPlayer(controller);
        if (ctrl == null) {
            safeStop(false);
            return;
        }
        // 対象が空なら再構築してみる
        if (targets.isEmpty()) {
            rebuildTargets();
            if (targets.isEmpty()) {
                clearSpectatorTarget(ctrl);
                if (manualAnnounce) ctrl.sendMessage("対象プレイヤーがいません。");
                return;
            }
        }

        // 今の対象が無効ならスキップ
        int safety = 0;
        while (safety++ < 64) {
            targetIndex = (targetIndex + 1) % targets.size();
            Player candidate = Bukkit.getPlayer(targets.get(targetIndex));
            if (candidate != null && candidate.isOnline() && isValidTarget(candidate)) {
                setSpectatorTarget(ctrl, candidate);
                if (manualAnnounce) {
                    ctrl.sendMessage("👀 視点切替: " + candidate.getName());
                }
                return;
            }
            // 無効ならリストから除外して続行
            targets.remove(targetIndex);
            if (targets.isEmpty()) break;
            targetIndex = (targetIndex - 1 + targets.size()) % targets.size(); // インデックス補正
        }
        // ここに来るのは全無効
        clearSpectatorTarget(ctrl);
        if (manualAnnounce) ctrl.sendMessage("対象プレイヤーがいません。");
    }

    private void rebuildTargets() {
        targets.clear();
        // OP/権限除外は含めず、観戦者・除外権限持ち等は排除
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (controller != null && controller.equals(pl.getUniqueId())) continue; // 自分は除外
            if (!isValidTarget(pl)) continue;
            targets.add(pl.getUniqueId());
        }
        // 安定のためソート（名前順）
        targets.sort(Comparator.comparing(uuid -> {
            Player pl = Bukkit.getPlayer(uuid);
            return pl != null ? pl.getName() : uuid.toString();
        }));
        if (targets.isEmpty()) targetIndex = -1;
        else targetIndex = Math.min(Math.max(targetIndex, -1), targets.size() - 1);
    }

    private boolean isValidTarget(Player pl) {
        if (!pl.isOnline()) return false;
        if (pl.getGameMode() == GameMode.SPECTATOR) return false;
        if (pl.hasPermission(PERM_EXEMPT)) return false; // 除外権限
        return true;
    }

    // ====== イベント連動（対象の離脱/死亡時に視点を進める） ======
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (controller == null) return;
        UUID q = e.getPlayer().getUniqueId();
        if (targets.remove(q)) {
            if (targetIndex >= targets.size()) targetIndex = targets.size() - 1;
            Player ctrl = Bukkit.getPlayer(controller);
            if (ctrl != null && ctrl.getGameMode() == GameMode.SPECTATOR) {
                Bukkit.getScheduler().runTask(this, () -> goNextTarget(false));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (controller == null) return;
        Player p = e.getPlayer();
        if (isValidTarget(p)) {
            targets.add(p.getUniqueId());
            targets.sort(Comparator.comparing(uuid -> {
                Player pl = Bukkit.getPlayer(uuid);
                return pl != null ? pl.getName() : uuid.toString();
            }));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (controller == null) return;
        Player dead = e.getEntity();
        if (targets.contains(dead.getUniqueId())) {
            // 死亡したら即次へ
            Bukkit.getScheduler().runTask(this, () -> goNextTarget(false));
        }
    }
}
