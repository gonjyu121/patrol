package dev.gonjy.patrolspectator;

import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EndResetManagerTest {

    private ServerMock server;
    private PatrolSpectatorPlugin plugin;
    private EndResetManager endResetManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        endResetManager = plugin.getEndResetManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testEndResetManagerCreation() {
        assertNotNull(endResetManager);
    }

    @Test
    void testCountdownAndCancel() {
        assertNotNull(endResetManager);
        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());

        endResetManager.forceReset();
        assertTrue(endResetManager.getRemainingResetTimeMillis() > 0);

        endResetManager.cancelReset();
        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());
        assertFalse(endResetManager.isResetting());
    }

    @Test
    void testLiveDragonSpawnCancelsAutomaticCountdown() {
        WorldMock endWorld = createEndWorld();
        endResetManager.startAutomaticResetCountdown("test death");
        assertTrue(endResetManager.getRemainingResetTimeMillis() > 0);

        EnderDragon dragon = endWorld.spawn(endWorld.getSpawnLocation(), EnderDragon.class);
        endResetManager.onDragonSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.CUSTOM));

        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());
        assertEquals("NONE", plugin.getConfig().getString("end.scheduledResetCause"));
    }

    @Test
    void testLiveDragonDoesNotCancelManualCountdown() {
        WorldMock endWorld = createEndWorld();
        endResetManager.forceReset();

        EnderDragon dragon = endWorld.spawn(endWorld.getSpawnLocation(), EnderDragon.class);
        endResetManager.onDragonSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.CUSTOM));

        assertTrue(endResetManager.getRemainingResetTimeMillis() > 0);
        assertEquals("MANUAL", plugin.getConfig().getString("end.scheduledResetCause"));
    }

    @Test
    void testOneDragonDeathDoesNotScheduleResetWhileAnotherLives() {
        WorldMock endWorld = createEndWorld();
        endWorld.spawn(endWorld.getSpawnLocation(), EnderDragon.class);
        EnderDragon deadDragon = mock(EnderDragon.class);
        when(deadDragon.getWorld()).thenReturn(endWorld);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(deadDragon);

        endResetManager.onDragonDeath(event);
        server.getScheduler().performOneTick();

        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());
    }

    @Test
    void testLegacyPendingResetIsDiscarded() {
        plugin.getConfig().set("end.scheduledResetTime", System.currentTimeMillis() + 60_000L);
        plugin.getConfig().set("end.scheduledResetCause", null);

        EndResetManager reloadedManager = new EndResetManager(plugin);

        assertEquals(-1, reloadedManager.getRemainingResetTimeMillis());
        assertEquals("NONE", plugin.getConfig().getString("end.scheduledResetCause"));
    }

    @Test
    void testLiveDragonDetectionUsesLoadedEntities() {
        World endWorld = mock(World.class);
        DragonBattle battle = mock(DragonBattle.class);
        when(endWorld.getEntitiesByClass(EnderDragon.class)).thenReturn(java.util.List.of());
        when(endWorld.getEnderDragonBattle()).thenReturn(battle);
        when(battle.getEnderDragon()).thenReturn(null);
        assertFalse(endResetManager.hasLiveDragon(endWorld));

        EnderDragon dragon = mock(EnderDragon.class);
        when(dragon.isValid()).thenReturn(true);
        when(dragon.isDead()).thenReturn(false);
        when(endWorld.getEntitiesByClass(EnderDragon.class)).thenReturn(java.util.List.of(dragon));

        assertTrue(endResetManager.hasLiveDragon(endWorld));
    }

    private WorldMock createEndWorld() {
        WorldMock endWorld = new WorldMock();
        endWorld.setName("world_the_end");
        endWorld.setEnvironment(World.Environment.THE_END);
        server.addWorld(endWorld);
        return endWorld;
    }
}
