package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingDisplaySystemTest {

    @Test
    void displaysDungeonRankingEvenWhenThereAreNoRecords() {
        assertTrue(RankingDisplaySystem.shouldDisplayDungeonRanking(Collections.emptyList()));
    }

    @Test
    void skipsOnlyUninitializedDungeonRanking() {
        assertFalse(RankingDisplaySystem.shouldDisplayDungeonRanking(null));
    }
}
