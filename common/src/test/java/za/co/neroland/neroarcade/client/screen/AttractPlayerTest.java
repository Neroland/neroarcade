package za.co.neroland.neroarcade.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Replay;
import za.co.neroland.neroarcade.runtime.ShippedReplays;

/**
 * The gallery's attract loop plays a shipped replay exactly: title card, the run one step per tick to the replay's
 * pinned hash, the held last frame, the scores, then the same run again from the top.
 */
class AttractPlayerTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void everyShippedReplayLoopsToItsPinnedHash() {
        for (Replay replay : ShippedReplays.all().values()) {
            AttractPlayer player = AttractPlayer.of(replay);
            assertNotNull(player, replay.gameDef().title());
            assertEquals(AttractPlayer.Phase.TITLE, player.phase());
            assertFalse(player.showsMatch());
            int ticks = 0;
            int cycle = player.cycleTicks();
            long endHash = 0;
            for (int loop = 0; loop < 2; loop++) {
                while (player.phase() != AttractPlayer.Phase.HOLD) {
                    player.advance();
                    ticks++;
                }
                assertEquals(replay.finalTick(), player.steps(), replay.gameDef().title());
                assertEquals(replay.finalHash(), player.state().stateHash(),
                        replay.gameDef().title() + ": the demo is the replay, bit for bit");
                assertTrue(player.showsMatch());
                endHash = player.state().stateHash();
                while (player.loops() == loop) {
                    player.advance();
                    ticks++;
                }
                assertEquals(AttractPlayer.Phase.TITLE, player.phase());
            }
            assertEquals(2 * cycle, ticks, replay.gameDef().title() + ": one cycle is title + run + hold + scores");
            assertEquals(replay.finalHash(), endHash);
        }
    }

    @Test
    void theCycleSpendsItsTimeOnTheGame() {
        for (Replay replay : ShippedReplays.all().values()) {
            AttractPlayer player = AttractPlayer.of(replay);
            int framing = AttractPlayer.TITLE_TICKS + AttractPlayer.HOLD_TICKS + AttractPlayer.SCORES_TICKS;
            assertTrue(player.cycleTicks() - framing >= 20 * 20, replay.gameDef().title() + " runs at least 20 s");
        }
    }
}
