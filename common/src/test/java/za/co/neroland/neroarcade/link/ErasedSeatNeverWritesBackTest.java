package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.board.BoardTestAccess;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.cabinet.HostEnv;
import za.co.neroland.neroarcade.cabinet.MatchHost;
import za.co.neroland.neroarcade.cabinet.MatchListener;
import za.co.neroland.neroarcade.cabinet.Seat;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * Compliance review F1: a player erased mid-way through a two-seat bus match is never written back. When the other
 * seat finishes, the board, the personal bests and the displaced list hold nothing for the erased player, and the
 * link pipeline raises no {@code MATCH_RESULT} or {@code RECORD_BEATEN} for them. A walk-out ({@code ESC}) keeps 003
 * §4.4's "best but no top row".
 */
class ErasedSeatNeverWritesBackTest {

    private static final UUID A = LinkFixtures.ALICE;
    private static final UUID B = LinkFixtures.BOB;
    private static final BlockPos HOST = new BlockPos(10, 64, -3);
    private static final BlockPos NODE = new BlockPos(12, 64, -3);
    private static final long NOW = 1_792_000_000L;
    /** Countdown plus the 10-second rule, with slack. */
    private static final int MAX_TICKS = 60 + 10 * 20 + 80;

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    /** What one run left behind. */
    private record Run(LeaderboardTable table, MatchListener.MatchEnd end, RecordOutcome outcome,
            FakeLinkContext ctx, ArcadeEvents.Pipeline events) {
    }

    @Test
    void erasedSeatLeavesNoBestNoRowAndNoEvent() {
        Run run = play(DropReason.ERASED, true);

        assertFalse(run.table().holds(A), "no row, best or bestAt for the erased player");
        assertTrue(run.table().boardsWithBest(A).isEmpty());
        assertTrue(run.table().holds(B), "the bystander's result is recorded");
        assertTrue(run.end().result().seats().stream().noneMatch(s -> s.uuid().equals(A)),
                "the match result carries no line for the erased player");
        assertTrue(run.outcome().displaced().stream().noneMatch(d -> d.player().equals(A)), "nobody displaced A");
        assertNoEventFor(run, A);
        assertFalse(run.events().events(B, 20).isEmpty(), "the bystander still gets their MATCH_RESULT");
    }

    @Test
    void operatorKickRecordsNothingEither() {
        Run run = play(DropReason.ADMIN, false);
        assertFalse(run.table().holds(A));
        assertNoEventFor(run, A);
    }

    @Test
    void walkOutKeepsTheBestButNoTopRow() {
        Run run = play(DropReason.ESC, false);
        assertTrue(run.table().holds(A), "a walk-out keeps the personal best (003 §4.4)");
        LeaderboardTable.Board board = run.table().board(run.end().result().cartridgeId()).orElseThrow();
        assertTrue(board.personalBest().containsKey(A));
        assertTrue(board.top().stream().noneMatch(e -> e.uuid().equals(A)), "but never a top row");
    }

    /**
     * Seats A and B on a two-seat coop bus, runs until A has scored, drops A for {@code why} (for an erasure the
     * stores are erased first, as {@code NeroArcadeData} does: stores, then seats), lets B finish and feeds the
     * match end through a real event pipeline.
     *
     * @param seedARow give A a top row on this board before the match, so a displacement would be visible
     */
    private Run play(DropReason why, boolean seedARow) {
        ResolvedGame game = twoSeatGame();
        LeaderboardTable table = new LeaderboardTable();
        String cartridge = game.def().id();
        if (seedARow) {
            BoardTestAccess.record(table, new BusMatchResult(game.def().lineageRoot(), cartridge,
                    List.of(new BusMatchResult.SeatResult(A, 0, 1, 1, false)), 200, 1L,
                    za.co.neroland.neroarcade.runtime.MatchResult.Outcome.WIN, BusMatchResult.EndedBy.RULE, 0L),
                    NOW - 100);
            assertTrue(table.holds(A));
        }
        List<MatchListener.MatchEnd> ends = new ArrayList<>();
        RecordOutcome[] written = {RecordOutcome.NONE};
        Env env = new Env();
        MatchHost host = new MatchHost(env, HOST, game, result -> {
            written[0] = BoardTestAccess.record(table, result, NOW);
            return written[0];
        }, 2).listener(ends::add);
        assertTrue(host.bind(0, new Seat(0, A, 0, HOST)));
        assertTrue(host.bind(1, new Seat(1, B, 0, NODE)));
        host.pressStart(0);
        host.pressStart(1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());

        int ticks = 0;
        while (ticks < MAX_TICKS && host.phase() != BusPhase.PLAYING) {
            env.time++;
            host.tick();
            ticks++;
        }
        assertEquals(BusPhase.PLAYING, host.phase());
        // a survive game scores at the end; put points on both seats now, as a pickup would
        host.runtime().state().addScore(0, 7);
        host.runtime().state().addScore(1, 3);
        HostTicks.run(host, env, 5);
        ticks += 5;
        assertEquals(BusPhase.PLAYING, host.phase());
        assertTrue(host.runtime().state().score(0) > 0, "seat A has scored before it is dropped");

        if (why == DropReason.ERASED) {
            table.erase(A); // the leaderboard store runs before the seats eraser
        }
        host.unbind(0, why);
        assertEquals(BusPhase.PLAYING, host.phase(), "B keeps playing");
        while (ticks < MAX_TICKS * 2 && host.phase().running()) {
            env.time++;
            host.tick();
            ticks++;
        }
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(1, ends.size(), "the match finished and was reported once");
        MatchListener.MatchEnd end = ends.get(0);

        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(end.result().cartridgeId(), end.result().lineageRoot(),
                game.def().title(), "minecraft:overworld", 2, end.result().seats(), end.result().outcome().name(),
                end.outcome(), end.hotSeat()));
        events.tick();
        assertNotNull(written[0]);
        return new Run(table, end, written[0], ctx, events);
    }

    private static void assertNoEventFor(Run run, UUID player) {
        List<JsonObject> section = run.events().events(player, 20);
        assertTrue(section.isEmpty(), "no MATCH_RESULT/RECORD_BEATEN in the events section: " + section);
        assertTrue(run.ctx().published.stream().noneMatch(e -> player.equals(e.playerId())), "nothing on the bus");
        assertFalse(run.ctx().alerts.containsKey(player), "no alert");
        assertFalse(run.events().holdsAnyFor(player), "no coalescer window");
    }

    private static ResolvedGame twoSeatGame() {
        TestModules.installShipped();
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.RULE, List.of(new SlotEntry(ModuleDefaults.RULE,
                Map.of("seconds", new PropertyValue.IntValue(10)))));
        slots.put(ModuleType.MULTIPLAYER, List.of(new SlotEntry(
                Identifier.fromNamespaceAndPath("neroarcade", "multiplayer/coop"),
                Map.of("seats", new PropertyValue.IntValue(2)))));
        GameDef def = TestModules.def(slots);
        try {
            return CartridgeValidator.validate(def);
        } catch (ValidationError e) {
            throw new AssertionError(e);
        }
    }

    private static final class HostTicks {
        static void run(MatchHost host, Env env, int n) {
            for (int i = 0; i < n; i++) {
                env.time++;
                host.tick();
            }
        }
    }

    /** A plain-JVM host environment: a manual clock, always powered, packets discarded. */
    private static final class Env implements HostEnv {
        long time;

        @Override
        public long gameTime() {
            return time;
        }

        @Override
        public long newSeed() {
            return 0x4E45524FL;
        }

        @Override
        public Collection<UUID> viewers() {
            return List.of();
        }

        @Override
        public void send(UUID player, CustomPacketPayload payload) {
        }

        @Override
        public boolean powered() {
            return true;
        }
    }
}
