package dev.gonjy.patrolspectator;

import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndGameManagerTest {

    private ServerMock server;
    private PatrolSpectatorPlugin plugin;
    private EndGameManager endGameManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        endGameManager = plugin.getEndGameManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testEndGameManagerCreation() {
        assertNotNull(endGameManager);
    }

    @Test
    void testBossBarLifecycle() {
        WorldMock endWorld = new WorldMock();
        endWorld.setName("world_the_end");
        endWorld.setEnvironment(World.Environment.THE_END);
        server.addWorld(endWorld);

        Player player = server.addPlayer();
        player.teleport(endWorld.getSpawnLocation());

        EnderDragon dragon = endWorld.spawn(endWorld.getSpawnLocation(), EnderDragon.class);
        assertNotNull(dragon);

        // ボスバー更新
        endGameManager.updateBossBar(dragon);

        // ダメージ時の更新
        dragon.setHealth(100.0);
        endGameManager.updateBossBar(dragon);

        // ボスバー削除
        endGameManager.removeBossBar();

        // クリーンアップ
        endGameManager.shutdown();
    }
}
