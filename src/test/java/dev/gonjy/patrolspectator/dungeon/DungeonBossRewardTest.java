package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonBossRewardTest {

    @Test
    void acceptsPlayerKillWhenBossAndKillerAreInsideDungeon() {
        assertTrue(DungeonListener.isValidBossDefeat(true, true, true));
    }

    @Test
    void rejectsEnvironmentalDeath() {
        assertFalse(DungeonListener.isValidBossDefeat(false, false, true));
    }

    @Test
    void rejectsKillFromDungeonSurfaceOrOutside() {
        assertFalse(DungeonListener.isValidBossDefeat(true, false, true));
    }

    @Test
    void rejectsBossThatEscapedDungeon() {
        assertFalse(DungeonListener.isValidBossDefeat(true, true, false));
    }
}
