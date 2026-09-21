package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankingDisplaySystemTest {

    @Test
    void displaysDungeonRankingEvenWhenThereAreNoRecords() {
        assertTrue(RankingDisplaySystem.shouldDisplayDungeonRanking(Collections.emptyList()));
    }

    @Test
    void skipsOnlyUninitializedDungeonRanking() {
        assertFalse(RankingDisplaySystem.shouldDisplayDungeonRanking(null));
    }

    @Test
    void advertisesVerifiedFloorsWithoutImplyingEntryBan() {
        assertEquals("死の迷宮は地下B22まで主要構造を確認！挑戦者募集中！",
                RankingDisplaySystem.formatDungeonAnnouncement(false, true, 22, 22));
        assertEquals("死の迷宮は主要構造 21/22階を確認！挑戦者募集中！",
                RankingDisplaySystem.formatDungeonAnnouncement(false, false, 22, 21));
        assertNull(RankingDisplaySystem.formatDungeonAnnouncement(true, false, 22, 10));
        assertNull(RankingDisplaySystem.formatDungeonAnnouncement(false, false, 22, 0));
    }
}
