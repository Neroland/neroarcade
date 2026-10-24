package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * Final review H1: a module hook that throws on the server ends the match ABANDONED — no board write, no listener, a
 * plain message to the seat — and nothing escapes {@link MatchHost#tick()}; the host does not fault again on the
 * next tick, and a fresh START runs a new match.
 */
class RuntimeFaultTest {

    /** A hazard whose tick throws from simulated tick {@code failAt} on (or whose match start throws). */
    private static HazardModule faulty(int failAt, boolean failOnStart) {
        return new HazardModule() {
            @Override
            public Identifier id() {
                return TestModules.id("hazard/test_faulty");
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of();
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
                if (failOnStart) {
                    throw new IllegalStateException("test fault at start");
                }
            }

            @Override
            public void tick(ModuleContext ctx) {
                if (ctx.tick() >= failAt) {
                    throw new ArrayIndexOutOfBoundsException("test fault at tick " + ctx.tick());
                }
            }
        };
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void aThrowingChipEndsTheMatchCleanly() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        ResolvedGame game = TestModules.resolvedOf(faulty(30, false));
        int[] listened = {0};
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, game, writer).listener(end -> listened[0]++);
        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS)));
        assertEquals(BusPhase.COUNTDOWN, host.phase(), "a solo cabinet starts on use");

        assertDoesNotThrow(() -> HostFixtures.tick(host, env, 60 + 40), "nothing escapes tick()");
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, host.lastResult().orElseThrow().outcome());
        assertEquals(0, writer.calls, "no board write");
        assertEquals(0, listened[0], "no listener call (no events, no plays)");
        assertEquals(1, host.faults());
        assertNull(host.runtime(), "the runtime is discarded: nothing steps it again");
        assertNotNull(host.seat(0), "the seat is still bound");
        assertEquals(List.of(new FakeHostEnv.Message(HostFixtures.ALICE, MatchHost.RUNTIME_FAILED_KEY)), env.messages);

        // the results strip runs out without a second fault
        assertDoesNotThrow(() -> HostFixtures.tick(host, env, 50));
        assertEquals(1, host.faults(), "no fault per tick");

        // a fresh START runs the game again; it faults again, cleanly, once
        host.pressStart(0);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertDoesNotThrow(() -> HostFixtures.tick(host, env, 60 + 40));
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(2, host.faults());
        assertEquals(0, writer.calls);
    }

    @Test
    void aChipThatThrowsAtMatchStartIsAbandonedToo() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, TestModules.resolvedOf(faulty(0, true)), writer);
        assertDoesNotThrow(() -> host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS)));
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(MatchResult.Outcome.ABANDONED, host.lastResult().orElseThrow().outcome());
        assertEquals(1, host.faults());
        assertEquals(0, writer.calls);
        assertDoesNotThrow(() -> HostFixtures.tick(host, env, MatchHost.RESULTS_TICKS + 5));
        assertEquals(1, host.faults());
    }

    @Test
    void aFaultOnALinkedBusTellsEverySeat() {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        TestModules.installShipped();
        ResolvedGame game = TestModules.resolvedOf(faulty(10, false),
                za.co.neroland.neroarcade.module.pack.multiplayer.CoopMultiplayer.INSTANCE);
        MatchHost host = new MatchHost(env, HostFixtures.POS, game, writer, 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.pressStart(0);
        host.pressStart(1);
        assertEquals(BusPhase.COUNTDOWN, host.phase());
        assertDoesNotThrow(() -> HostFixtures.tick(host, env, 60 + 20));
        assertEquals(BusPhase.RESULTS, host.phase());
        assertEquals(0, writer.calls);
        assertEquals(2, env.messages.size());
        assertTrue(env.messages.stream().allMatch(m -> m.langKey().equals(MatchHost.RUNTIME_FAILED_KEY)));
    }
}
