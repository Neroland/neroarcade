package za.co.neroland.neroarcade.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.MatchListener;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.multiplayer.CoopMultiplayer;
import za.co.neroland.neroarcade.module.pack.multiplayer.VersusMultiplayer;
import za.co.neroland.neroarcade.privacy.Tombstones;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §9: threshold crossings carry a place or system as their scope — a lineage root or {@code "exchange"} — never a
 * player; values climb the rungs; player gates open once (Core dedups, NeroArcade never re-opens a tombstone); the
 * {@code first_versus_win} gate needs a non-dropped win on a versus game against another human.
 */
class MilestonesTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
    private static final Pattern UUID_SHAPED = Pattern.compile("(?i).*[0-9a-f]{8}-[0-9a-f]{4}.*");
    private static final String ROOT = "abcdefghijklmnop";

    private final List<ThresholdEvents.ThresholdCrossing> fired = new ArrayList<>();
    private final Set<String> opened = new HashSet<>();
    private int openCalls;

    @BeforeEach
    void sink() {
        ArcadeMilestones.setSink(new ArcadeMilestones.Sink() {
            @Override
            public void fire(ThresholdEvents.ThresholdCrossing crossing) {
                fired.add(crossing);
            }

            @Override
            public boolean openGate(MinecraftServer server, UUID player, Identifier gate) {
                openCalls++;
                return opened.add(player + "|" + gate); // Core semantics: true only when newly opened
            }
        });
    }

    @AfterEach
    void restore() {
        ArcadeMilestones.setSink(null);
        TestModules.reset();
    }

    @Test
    void thresholdEventsNeverCarryAPlayerScope() {
        ArcadeMilestones.matchWon(ROOT, 1);
        ArcadeMilestones.matchWon(ROOT, 12);
        ArcadeMilestones.cartridgePublished(1);
        ArcadeMilestones.cartridgePublished(57);
        ArcadeMilestones.recordSet(ROOT, 3);
        assertFalse(ArcadeMilestones.matchWon("", 1), "no scope, no crossing");
        assertEquals(5, fired.size());
        for (ThresholdEvents.ThresholdCrossing c : fired) {
            assertFalse(UUID_SHAPED.matcher(c.scope()).matches(), "scope is never a player: " + c.scope());
            assertTrue(c.rising());
            assertTrue(c.value() >= c.threshold());
            assertTrue(ArcadeMilestones.channels().contains(c.channel()));
        }
        assertEquals(ArcadeMilestones.EXCHANGE_SCOPE, fired.get(2).scope());
        assertEquals(10L, fired.get(1).threshold(), "12 wins: rung 10");
        assertEquals(50L, fired.get(3).threshold(), "57 listings: rung 50");
        assertEquals(1L, fired.get(4).threshold(), "3 records: rung 1");
    }

    @Test
    void gatesOpenedOnce() {
        assertTrue(ArcadeMilestones.firstCartridge(null, ALICE));
        assertFalse(ArcadeMilestones.firstCartridge(null, ALICE), "the second save opens nothing new");
        assertTrue(ArcadeMilestones.firstPublished(null, ALICE));
        assertFalse(ArcadeMilestones.firstPublished(null, Tombstones.ERASED_AUTHOR), "never for a tombstone");
        assertFalse(ArcadeMilestones.firstVersusWin(null, null));
        assertEquals(3, openCalls, "the tombstone and null never reach Core");
    }

    private static MatchListener.MatchEnd end(boolean versus, boolean hotSeat, MatchResult.Outcome outcome,
            BusMatchResult.SeatResult... seats) {
        TestModules.installShipped();
        var game = TestModules.resolvedOf(versus ? VersusMultiplayer.INSTANCE : CoopMultiplayer.INSTANCE);
        BusMatchResult result = new BusMatchResult(ROOT, ROOT, List.of(seats), 400, 1L, outcome,
                BusMatchResult.EndedBy.RULE, 0L);
        return new MatchListener.MatchEnd(result, game, RecordOutcome.NONE, hotSeat, true, BlockPos.ZERO);
    }

    @Test
    void firstVersusWinNeedsAHumanOpponent() {
        var alice = new BusMatchResult.SeatResult(ALICE, 0, 50, 1, false);
        var bob = new BusMatchResult.SeatResult(BOB, 1, 30, 2, false);
        assertEquals(ALICE, ArcadeMatchEnds.versusWinner(end(true, false, MatchResult.Outcome.WIN, alice, bob))
                .orElseThrow());
        assertTrue(ArcadeMatchEnds.versusWinner(end(true, false, MatchResult.Outcome.WIN, alice)).isEmpty(),
                "one human: the other seat was the runtime opponent");
        assertTrue(ArcadeMatchEnds.versusWinner(end(false, false, MatchResult.Outcome.WIN, alice, bob)).isEmpty(),
                "co-op is not versus");
        assertTrue(ArcadeMatchEnds.versusWinner(end(true, true, MatchResult.Outcome.WIN, alice, bob)).isEmpty(),
                "never hot-seat");
        assertTrue(ArcadeMatchEnds.versusWinner(end(true, false, MatchResult.Outcome.DRAW, alice, bob)).isEmpty());
        var droppedWinner = new BusMatchResult.SeatResult(ALICE, 0, 50, 1, true);
        assertTrue(ArcadeMatchEnds.versusWinner(end(true, false, MatchResult.Outcome.WIN, droppedWinner, bob))
                .isEmpty(), "a walk-out never earns it");
    }
}
