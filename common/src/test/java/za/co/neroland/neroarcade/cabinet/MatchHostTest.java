package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/** 003 §4 on a loopback bus: the solo lifecycle and the single leaderboard write path. */
class MatchHostTest {

    private FakeHostEnv env;
    private HostFixtures.CountingWriter writer;
    private MatchHost host;

    @BeforeEach
    void setUp() {
        env = new FakeHostEnv();
        writer = new HostFixtures.CountingWriter();
        host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), writer);
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void soloStartsOnUseAndRunsIdleToResults() {
        assertEquals(BusPhase.IDLE, host.phase());
        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0)));
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "solo: the use is the start");
        assertEquals(1, host.matchId());

        HostFixtures.tick(host, env, 60);
        assertEquals(BusPhase.PLAYING, host.phase());

        HostFixtures.tick(host, env, HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.WIN, host.lastResult().orElseThrow().outcome());

        assertEquals(1, writer.calls, "finish writes exactly once");
        BusMatchResult result = writer.last;
        assertEquals(1, result.seats().size());
        assertEquals(HostFixtures.ALICE, result.seats().get(0).uuid());
        assertEquals(1, result.seats().get(0).rank());
        assertFalse(result.seats().get(0).dropped());
        assertEquals(host.game().def().lineageRoot(), result.lineageRoot());
        assertEquals(env.seed, result.seed());
        assertEquals(MatchResult.Outcome.WIN, result.outcome());
        assertEquals(host.lastResult().orElseThrow().finalHash(), result.finalHash());

        HostFixtures.tick(host, env, MatchHost.RESULTS_TICKS);
        assertEquals(BusPhase.LOBBY, host.phase(), "the seated player stays: back to the lobby");
        assertEquals(1, writer.calls, "results timing out writes nothing more");
    }

    @Test
    void rematchFromResultsStartsANewMatch() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        host.pressStart(0);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertEquals(2, host.matchId());
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(2, writer.calls, "one write per match");
    }

    @Test
    void startButtonEdgeRematchesFromResults() {
        Seat seat = new Seat(0, HostFixtures.ALICE, 0);
        host.bind(0, seat);
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
        host.input(0, SeatInput.of(SeatInput.START, 0, 0));
        HostFixtures.tick(host, env, 1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
    }

    @Test
    void abandonedMatchNeverWrites() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        assertEquals(BusPhase.PLAYING, host.phase());
        host.unbind(0, DropReason.ESC);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, host.lastResult().orElseThrow().outcome());
        HostFixtures.tick(host, env, MatchHost.RESULTS_TICKS + 10);
        assertEquals(0, writer.calls);
        assertEquals(BusPhase.IDLE, host.phase());
    }

    @Test
    void forcedEndIsAbandonedAndWritesNothing() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 100);
        host.end(MatchResult.Outcome.ABANDONED);
        host.end(MatchResult.Outcome.ABANDONED);
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, host.lastResult().orElseThrow().outcome());
        HostFixtures.tick(host, env, 300);
        assertEquals(0, writer.calls);
    }

    @Test
    void bindSendsLegendAndLeaverIsToldTheySeatNoMore() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        List<HudLegendPayload> legends = env.sentTo(HostFixtures.ALICE, HudLegendPayload.class);
        assertEquals(1, legends.size());
        assertFalse(legends.get(0).legendLangKeys().isEmpty());
        List<MatchPhasePayload> phases = env.sentTo(HostFixtures.ALICE, MatchPhasePayload.class);
        assertEquals(0, phases.get(phases.size() - 1).mySeat());

        host.unbind(0, DropReason.ESC);
        phases = env.sentTo(HostFixtures.ALICE, MatchPhasePayload.class);
        assertTrue(phases.stream().anyMatch(p -> p.mySeat() == -1));
    }

    @Test
    void lateJoinerNeverEntersARunningMatch() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        assertFalse(host.bind(0, new Seat(0, HostFixtures.BOB, 0)), "seat taken");
        host.unbind(0, DropReason.ESC);
        assertEquals(BusPhase.IDLE, host.phase(), "a drop in COUNTDOWN below the minimum aborts");
        assertTrue(host.bind(0, new Seat(0, HostFixtures.BOB, 0)));
        HostFixtures.tick(host, env, 70);
        assertFalse(host.bind(0, new Seat(0, HostFixtures.ALICE, 0)));
    }

    @Test
    void snapshotsReachViewersAndANewViewerGetsAKeyframe() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 100);
        assertFalse(env.sentTo(HostFixtures.ALICE, SnapshotPayload.class).isEmpty(), "the seated player is a viewer");

        env.viewers.add(HostFixtures.BOB);
        HostFixtures.tick(host, env, 4);
        List<SnapshotPayload> bob = env.sentTo(HostFixtures.BOB, SnapshotPayload.class);
        assertFalse(bob.isEmpty());
        assertTrue(SnapshotCodec.decode(bob.get(0).body(), null).isPresent(),
                "the first update a new viewer receives decodes without a base (a keyframe)");
        assertEquals(host.matchId(), bob.get(0).matchId());
        assertEquals(-1, env.sentTo(HostFixtures.BOB, MatchPhasePayload.class).get(0).mySeat(), "a spectator");
    }

    @Test
    void noPowerPausesWithoutChangingTheState() {
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        assertNotNull(host.runtime());
        long hash = host.runtime().state().stateHash();
        env.powered = false;
        host.input(0, SeatInput.of(SeatInput.RIGHT, 0, 0));
        HostFixtures.tick(host, env, 40);
        assertTrue(host.paused());
        assertEquals(hash, host.runtime().state().stateHash(), "a pause never alters the match");
        env.powered = true;
        HostFixtures.tick(host, env, 1);
        assertFalse(host.paused());
    }

    @Test
    void ranksShareTies() {
        assertEquals(List.of(2, 1, 2, 4), Arrays.stream(MatchHost.ranks(new int[] {5, 9, 5, 1}, 4)).boxed().toList());
    }
}
