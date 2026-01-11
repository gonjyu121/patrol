package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Random;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class EndGameManager implements Listener {

    private final JavaPlugin plugin;
    private final PlayerStatsStorage statsStorage;
    private final DiscordWebhookClient discordWebhookClient;
    private BukkitTask minionTask;
    private BukkitTask abilityTask;
    private BukkitTask roarTask;
    private final Random random = new Random();
    // 召喚したミニオンを追跡して、ドラゴン討伐時に道連れにする
    private final java.util.List<org.bukkit.entity.Entity> activeMinions = new java.util.ArrayList<>();
    // ドラゴンへのダメージを追跡
    private final java.util.Map<UUID, Double> dragonDamageMap = new java.util.HashMap<>();

    public EndGameManager(JavaPlugin plugin, PlayerStatsStorage statsStorage,
            DiscordWebhookClient discordWebhookClient) {
        this.plugin = plugin;
        this.statsStorage = statsStorage;
        this.discordWebhookClient = discordWebhookClient;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean isHardMode() {
        // "HARD" であればハードモード
        return "HARD".equalsIgnoreCase(plugin.getConfig().getString("end.difficulty", "NORMAL"));
    }

    @EventHandler
    public void onDragonSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        if (!event.getLocation().getWorld().getEnvironment().equals(World.Environment.THE_END)) {
            return;
        }

        if (isHardMode()) {
            EnderDragon dragon = (EnderDragon) event.getEntity();
            setupHardDragon(dragon);
            startMinionTask(dragon);
            startAbilityTask(dragon);
            startRoarTask(dragon);

            String message = "⚠ ヴォイド・ドラゴン (Void Dragon) が出現しました！ (HARD MODE)";
            Bukkit.broadcastMessage(ChatColor.RED + message);
            if (discordWebhookClient != null) {
                discordWebhookClient.send("🐉 **[ボス出現]** " + message);
            }
        }
    }

    private void setupHardDragon(EnderDragon dragon) {
        // 名前変更
        dragon.setCustomName(ChatColor.RED + "Void Dragon");
        dragon.setCustomNameVisible(true);

        // 体力強化 (通常200 -> 600)
        double maxHealth = 600.0;
        if (dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        }
        dragon.setHealth(maxHealth);

        // バフ付与 (耐性 & 攻撃力上昇)
        // 持続時間は実質無限 (Integer.MAX_VALUE tick)
        dragon.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 0)); // Lv1 (-20% dmg)
        dragon.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, 0)); // Lv1 (+3 dmg)
    }

    private void startMinionTask(EnderDragon dragon) {
        if (minionTask != null && !minionTask.isCancelled()) {
            minionTask.cancel();
        }
        activeMinions.clear();

        // 45秒ごとに取り巻きを召喚
        minionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                if (minionTask != null)
                    minionTask.cancel();
                return;
            }

            // クリーンアップ: 死んだミニオンをリストから除外
            activeMinions.removeIf(e -> e.isDead() || !e.isValid());

            Location spawnLoc = dragon.getLocation();

            // ウィザースケルトン3体 (地上)
            for (int i = 0; i < 3; i++) {
                spawnMinion(spawnLoc, EntityType.WITHER_SKELETON);
            }
            // ファントム2体 (空中)
            for (int i = 0; i < 2; i++) {
                spawnMinion(spawnLoc, EntityType.PHANTOM);
            }
            // ブレイズ2体 (空中or地上)
            for (int i = 0; i < 2; i++) {
                spawnMinion(spawnLoc, EntityType.BLAZE);
            }

            // プレイヤーへの通知
            for (Player p : dragon.getWorld().getPlayers()) {
                p.sendMessage(ChatColor.RED + "⚠ ヴォイド・ドラゴンが軍団を召喚しました！");
            }

        }, 45 * 20L, 45 * 20L);
    }

    private void startAbilityTask(EnderDragon dragon) {
        if (abilityTask != null && !abilityTask.isCancelled()) {
            abilityTask.cancel();
        }

        // 20秒ごとに Void Lightning (雷撃)
        abilityTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                if (abilityTask != null)
                    abilityTask.cancel();
                return;
            }

            World world = dragon.getWorld();
            boolean struck = false;
            for (Player p : world.getPlayers()) {
                // プレイヤーの位置に雷を落とす
                world.strikeLightning(p.getLocation());
                struck = true;
            }

            if (struck) {
                Bukkit.broadcastMessage(ChatColor.RED + "⚡ Void Lightning!!");
            }

        }, 20 * 20L, 20 * 20L);
    }

    private void startRoarTask(EnderDragon dragon) {
        if (roarTask != null && !roarTask.isCancelled()) {
            roarTask.cancel();
        }

        // 35秒ごとに Void Roar (虚無の咆哮)
        roarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                if (roarTask != null)
                    roarTask.cancel();
                return;
            }

            World world = dragon.getWorld();
            boolean roared = false;

            for (Player p : world.getPlayers()) {
                // サバイバル/アドベンチャーのプレイヤーのみ対象
                if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL
                        && p.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                    continue;
                }

                // サウンド再生 (ドラゴンの咆哮 + エンダーマンの叫び)
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);

                // 周囲のエンダーマンを激昂させる (32ブロック以内)
                world.getNearbyEntities(p.getLocation(), 32, 32, 32).stream()
                        .filter(e -> e instanceof org.bukkit.entity.Enderman)
                        .map(e -> (org.bukkit.entity.Enderman) e)
                        .forEach(enderman -> {
                            enderman.setTarget(p);
                            enderman.setScreaming(true);
                        });

                roared = true;
            }

            if (roared) {
                Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "📢 " + ChatColor.RED + "ヴォイド・ドラゴンの咆哮がエンドに響き渡った！");
                Bukkit.broadcastMessage(ChatColor.GRAY + "（周囲のエンダーマンが興奮している...）");
            }

        }, 35 * 20L, 35 * 20L);
    }

    private void spawnMinion(Location center, EntityType type) {
        World world = center.getWorld();
        if (world == null)
            return;

        // ランダムなオフセット
        double x = center.getX() + (random.nextDouble() * 10 - 5);
        double z = center.getZ() + (random.nextDouble() * 10 - 5);
        double y;

        if (type == EntityType.PHANTOM || type == EntityType.BLAZE) {
            // 空中 (ドラゴンの高さ + 少し上)
            y = center.getY() + 5 + random.nextDouble() * 5;
        } else {
            // 地上 (最高高度)
            y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        }

        Location loc = new Location(world, x, y, z);
        org.bukkit.entity.Entity minion = world.spawnEntity(loc, type);
        activeMinions.add(minion);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (!dragon.getWorld().getEnvironment().equals(World.Environment.THE_END)) {
            return;
        }

        Player damager = null;

        if (event instanceof EntityDamageByEntityEvent ebee) {
            org.bukkit.entity.Entity entity = ebee.getDamager();
            if (entity instanceof Player p) {
                damager = p;
            } else if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                damager = p;
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            // ベッド爆破などの場合、爆発地点の近くにいるプレイヤーを検索 (12ブロック以内)
            damager = dragon.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(dragon.getLocation()) < 12 * 12)
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(dragon.getLocation())))
                    .orElse(null);
        }

        if (damager != null) {
            UUID uuid = damager.getUniqueId();
            dragonDamageMap.put(uuid, dragonDamageMap.getOrDefault(uuid, 0.0) + event.getFinalDamage());
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }

        // 共通処理: 討伐数をカウント
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            // トドメがプレイヤーでない場合（ベッド爆発、落下ダメージ等）、周囲128ブロック以内の最も近いプレイヤーを探す
            killer = event.getEntity().getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(event.getEntity().getLocation()) < 128 * 128)
                    .min(Comparator
                            .comparingDouble(p -> p.getLocation().distanceSquared(event.getEntity().getLocation())))
                    .orElse(null);
        }

        if (killer != null) {
            statsStorage.addEnderDragonKill(killer.getUniqueId());
            statsStorage.ensureName(killer.getUniqueId(), killer.getName());
            plugin.getLogger().info("[Debug] Dragon kill recorded for: " + killer.getName() + " (Killer: "
                    + (event.getEntity().getKiller() != null ? "Direct" : "Nearby/Damage") + ")");
        } else {
            // ダメージマップから最大ダメージ者を探す
            UUID topDamagerId = dragonDamageMap.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse(null);

            if (topDamagerId != null) {
                killer = Bukkit.getPlayer(topDamagerId);
                if (killer != null) {
                    statsStorage.addEnderDragonKill(topDamagerId);
                    statsStorage.ensureName(topDamagerId, killer.getName());
                    plugin.getLogger().info("[Debug] Dragon kill recorded for top damager: " + killer.getName());
                }
            }
        }

        if (killer != null) {
            // 共通の討伐メッセージ (通常モードでも表示)
            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage(ChatColor.YELLOW + killer.getName() + " が エンダードラゴン を討伐しました！");
            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            plugin.getLogger().warning("[Debug] Ender Dragon death detected, but no player found to credit.");
        }

        // ハードモードの場合のみ報酬処理 (killerが特定できている場合)
        if (isHardMode() && killer != null) {
            // 称号付与
            statsStorage.setHardDragonSlayer(killer.getUniqueId(), true);

            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage(ChatColor.GOLD + "⚔️ 伝説の誕生！ ⚔️");
            Bukkit.broadcastMessage(ChatColor.YELLOW + killer.getName() + " が " + ChatColor.RED + "ヴォイド・ドラゴン"
                    + ChatColor.YELLOW + " を討伐しました！ (HARD MODE)");
            Bukkit.broadcastMessage(ChatColor.AQUA + "称号 [★] が付与されました！");
            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (discordWebhookClient != null) {
                discordWebhookClient.send(
                        "⚔️ **伝説の誕生！**\n" +
                                "**" + killer.getName() + "** が **ヴォイド・ドラゴン** を討伐しました！\n" +
                                "称号 **[★]** が付与されました！");
            }
        }

        shutdown();
    }

    public void shutdown() {
        if (minionTask != null) {
            minionTask.cancel();
            minionTask = null;
        }
        if (abilityTask != null) {
            abilityTask.cancel();
            abilityTask = null;
        }
        if (roarTask != null) {
            roarTask.cancel();
            roarTask = null;
        }

        // 残っているミニオンを抹消
        if (activeMinions != null) {
            for (org.bukkit.entity.Entity e : activeMinions) {
                if (e != null && e.isValid()) {
                    e.remove(); // 道連れ
                }
            }
            activeMinions.clear();
        }
        dragonDamageMap.clear();
    }
}
