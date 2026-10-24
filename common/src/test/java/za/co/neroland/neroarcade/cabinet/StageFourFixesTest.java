package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;
import za.co.neroland.neroarcade.server.ArcadeServer;

/**
 * Review 003b, the non-blocking fixes in the cabinet package: F8 (silent host loss releases the HUD), F9 (no power
 * for {@code sleepAfterTicks} ends the match and drops every seat with POWER), F10 (coin-op is charged before the
 * bind), F12 (hot-seat detection cached), L1 (keyframe amplification capped) and P1 (the seats eraser).
 */
class StageFourFixesTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
        SeatRegistry.SERVER.clear();
    }

    @Test
    void releasedPhaseUnseatsTheClient() {
        MatchPhasePayload released = MatchPhasePayload.released(HostFixtures.POS);
        assertEquals(-1, released.mySeat());
        assertEquals(BusPhase.IDLE, released.phase(), "IDLE: ClientMatches.seated() no longer returns it");
        assertEquals(HostFixtures.POS, released.host());
        assertEquals(HostFixtures.POS, released.seatAt());
    }

    @Test
    void noPowerForSleepAfterTicksEndsTheMatchWithPowerDrops() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), writer);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        assertTrue(host.phase().running());
        env.powered = false;
        HostFixtures.tick(host, env, Budgets.sleepAfterTicks() - 1);
        assertTrue(host.phase().running(), "paused, not yet dropped");
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, host.lastResult().orElseThrow().outcome());
        assertFalse(host.hasSeat());
        assertEquals(0, writer.calls, "an abandoned match never writes a board");
        assertEquals(List.of(new FakeHostEnv.Unseat(HostFixtures.POS, DropReason.POWER)), env.unseats);
    }

    @Test
    void aRefusedChargeBindsNothingAndStartsNothing() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(),
                new HostFixtures.CountingWriter());
        SeatRegistry registry = new SeatRegistry();
        SeatRegistry.SeatKey key = new SeatRegistry.SeatKey("o", HostFixtures.POS.asLong());
        int matchBefore = host.matchId();
        ArcadeCabinetBlockEntity.SeatResult result = SeatTake.payThenBind(registry, key, host, 0,
                new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS), () -> false);
        assertEquals(ArcadeCabinetBlockEntity.SeatResult.UNPAID, result);
        assertEquals(BusPhase.IDLE, host.phase(), "no countdown, no start sound");
        assertEquals(matchBefore, host.matchId());
        assertNull(host.seat(0));
        assertFalse(registry.isSeated(HostFixtures.ALICE), "the claim is released");

        int[] charged = {0};
        result = SeatTake.payThenBind(registry, key, host, 0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS),
                () -> ++charged[0] > 0);
        assertEquals(ArcadeCabinetBlockEntity.SeatResult.SEATED, result);
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "a solo cabinet starts on the paid use");
        // a taken seat is refused before any charge
        result = SeatTake.payThenBind(registry, key, host, 0, new Seat(0, HostFixtures.BOB, 0, HostFixtures.POS),
                () -> ++charged[0] > 0);
        assertEquals(ArcadeCabinetBlockEntity.SeatResult.TAKEN, result);
        assertEquals(1, charged[0], "never charged for a seat that was taken");
    }

    @Test
    void hotSeatDetectionIsStableAndCached() {
        var game = HostFixtures.tenSecondGame("turns", 2);
        boolean first = HotSeat.is(game);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, HotSeat.is(game));
        }
        assertTrue(first, "the turns chip is hot-seat");
        assertFalse(HotSeat.is(HostFixtures.tenSecondGame("coop", 2)));
    }

    @Test
    void aSpectatorOscillatingAtTheRangeEdgeCannotAmplifyKeyframes() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(),
                new HostFixtures.CountingWriter());
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        env.sent.clear();
        // 30 newcomers over 30 ticks: Bob steps in and out of range every tick
        for (int t = 0; t < 30; t++) {
            if (t % 2 == 0) {
                env.viewers.add(HostFixtures.BOB);
            } else {
                env.viewers.remove(HostFixtures.BOB);
            }
            HostFixtures.tick(host, env, 1);
        }
        long keyframes = env.sentTo(HostFixtures.ALICE, SnapshotPayload.class).stream()
                .filter(p -> SnapshotCodec.decode(p.body(), null).isPresent()).count();
        int natural = 30 / Math.max(1, Budgets.keyframeTicks()) + 1;
        assertTrue(keyframes <= 3 + natural, "forced keyframes are capped (" + keyframes + ")");
        // a late joiner still gets a keyframe soon after arriving
        env.viewers.add(HostFixtures.CAROL);
        HostFixtures.tick(host, env, MatchHost.KEYFRAME_MIN_GAP + 2);
        List<SnapshotPayload> carol = env.sentTo(HostFixtures.CAROL, SnapshotPayload.class);
        assertTrue(carol.stream().anyMatch(p -> SnapshotCodec.decode(p.body(), null).isPresent()),
                "the newcomer's keyframe is owed, not dropped");
    }

    @Test
    void theSeatsEraserUnseatsAndIsRegisteredForConformance() {
        SeatRegistry.SERVER.claim(HostFixtures.ALICE, new SeatRegistry.SeatKey("o", 1L));
        assertTrue(SeatRegistry.SERVER.isSeated(HostFixtures.ALICE));
        ArcadeServer.SEATS_ERASER.erase(null, HostFixtures.ALICE);
        assertFalse(SeatRegistry.SERVER.isSeated(HostFixtures.ALICE), "conformance probe");
        assertEquals("seats", ArcadeServer.SEATS_ERASER.name());
        assertSame(ArcadeServer.SEATS_ERASER, ArcadeServer.erasers().get(0));
        ArcadeServer.SEATS_ERASER.erase(null, HostFixtures.BOB); // an unknown player is fine
    }
}
