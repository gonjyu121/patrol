package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DungeonChampionRewardTest {

    private PatrolSpectatorPlugin plugin;

    @AfterEach
    void tearDown() {
        if (plugin != null) {
            File file = new File(plugin.getDataFolder(), "dungeon_stats.yml");
            if (file.exists()) {
                file.delete();
            }
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void permanentRewardSurvivesStorageReload() {
        MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        UUID playerId = UUID.randomUUID();
        DungeonStatsStorage first = new DungeonStatsStorage(plugin);

        assertTrue(first.grantPermanentKeepInventory(playerId, "Champion"));
        assertFalse(first.grantPermanentKeepInventory(playerId, "Champion"));

        DungeonStatsStorage reloaded = new DungeonStatsStorage(plugin);
        assertTrue(reloaded.hasPermanentKeepInventory(playerId));
    }

    @Test
    void permanentRewardKeepsItemsAndExperienceOnDeath() {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        List<ItemStack> drops = new ArrayList<>();
        drops.add(mock(ItemStack.class));
        when(event.getDrops()).thenReturn(drops);

        DungeonListener.applyPermanentKeepInventory(event);

        verify(event).setKeepInventory(true);
        verify(event).setKeepLevel(true);
        verify(event).setDroppedExp(0);
        assertTrue(drops.isEmpty());
    }

    @Test
    void blocksOnlySpawnLocationsInsideDungeon() {
        assertTrue(DungeonListener.shouldBlockSpawnChange(true));
        assertFalse(DungeonListener.shouldBlockSpawnChange(false));
    }
}
