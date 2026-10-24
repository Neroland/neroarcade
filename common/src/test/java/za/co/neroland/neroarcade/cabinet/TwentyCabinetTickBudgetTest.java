package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.bus.BusNode;
import za.co.neroland.neroarcade.bus.BusTopology;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Replay;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.ShippedReplays;

/**
 * 003b §12 item 5 (the lag items deferred to Stage 5), measured in a plain JVM with loose bounds — the numbers are
 * printed for the render-budget record; the in-game measurement procedure is in the planning notes.
 *
 * <ul>
 *   <li>20 awake solo hosts running the solo example cartridges, each with a seated player and four spectators
 *       (snapshots encoded and "sent" every snapshot interval), for 30 s of play: the server cost per tick for all
 *       twenty.</li>
 *   <li>Hub rediscovery on a full bus (16 cables, 4 cabinets, 4 marquees): the cost of one walk, which each hub
 *       pays once a second.</li>
 * </ul>
 */
class TwentyCabinetTickBudgetTest {

    /** A server tick is 50 ms; twenty arcade hosts must stay a small part of it even on a slow CI runner. */
    private static final double MAX_MS_PER_TICK = 10.0;
    private static final int HOSTS = 20;
    private static final int SPECTATORS = 4;
    private static final int[] DIRS = {SeatInput.UP, SeatInput.RIGHT, SeatInput.DOWN, SeatInput.LEFT, SeatInput.A};

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void twentyAwakeHostsCostLittleOfAServerTick() throws Exception {
        TestModules.installShipped();
        // the solo example cartridges (chasers, serpents, beams, stacks ...), dealt round the twenty cabinets
        List<ResolvedGame> games = new ArrayList<>();
        for (Replay replay : ShippedReplays.all().values()) {
            if (replay.seats() == 1) {
                games.add(CartridgeValidator.validate(replay.gameDef()));
            }
        }
        assertTrue(games.size() >= 6, "solo examples: " + games.size());
        List<MatchHost> hosts = new ArrayList<>();
        List<FakeHostEnv> envs = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            FakeHostEnv env = new FakeHostEnv();
            for (int s = 0; s < SPECTATORS; s++) {
                env.viewers.add(new UUID(i, s + 1));
            }
            MatchHost host = MatchHost.solo(env, new BlockPos(i * 3, 64, 0), games.get(i % games.size()),
                    new HostFixtures.CountingWriter());
            assertTrue(host.bind(0, new Seat(0, new UUID(i, 0), 0)));
            hosts.add(host);
            envs.add(env);
        }
        // countdown + 30 s of play, twice: the first pass warms the JIT, the better pass is reported
        int ticks = 60 + 30 * 20;
        long best = Long.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            long t0 = System.nanoTime();
            for (int t = 0; t < ticks; t++) {
                for (int i = 0; i < HOSTS; i++) {
                    envs.get(i).time++;
                    if (t % 5 == 0) {
                        // a restless player: a new direction every quarter second, START now and then (a rematch)
                        int dir = DIRS[(t / 5 + i) % DIRS.length];
                        hosts.get(i).input(0, SeatInput.of(dir | (t % 100 == 0 ? SeatInput.START : 0), 0, 0));
                    }
                    hosts.get(i).tick();
                }
                if (t % 100 == 0) {
                    envs.forEach(e -> e.sent.clear());
                }
            }
            best = Math.min(best, System.nanoTime() - t0);
        }
        double msPerTick = best / 1.0e6 / ticks;
        System.out.printf("20 awake hosts (solo examples) + %d spectators each: %.3f ms per server tick "
                + "(%.4f ms per host)%n", SPECTATORS, msPerTick, msPerTick / HOSTS);
        assertTrue(msPerTick < MAX_MS_PER_TICK, msPerTick + " ms per tick");
        for (MatchHost host : hosts) {
            assertTrue(host.matchId() >= 1, "every host started a match");
        }
    }

    @Test
    void hubRediscoveryOfAFullBusIsCheap() {
        Map<Long, BusNode> nodes = new HashMap<>();
        BlockPos hub = new BlockPos(0, 64, 0);
        nodes.put(hub.asLong(), BusNode.HUB);
        for (int i = 1; i <= BusTopology.MAX_CABLES; i++) {
            nodes.put(BlockPos.asLong(i, 64, 0), BusNode.CABLE);
        }
        for (int i = 0; i < BusTopology.MAX_CABINETS; i++) {
            nodes.put(BlockPos.asLong(2 + i * 3, 64, 1), BusNode.CABINET);
            nodes.put(BlockPos.asLong(2 + i * 3, 65, 0), BusNode.MARQUEE);
        }
        BusTopology first = BusTopology.discover(p -> nodes.getOrDefault(p.asLong(), BusNode.NONE), hub, p -> false);
        assertEquals(BusTopology.MAX_CABINETS, first.cabinets().size());
        assertEquals(BusTopology.MAX_MARQUEES, first.marquees().size());
        int walks = 5_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < walks; i++) {
            BusTopology.discover(p -> nodes.getOrDefault(p.asLong(), BusNode.NONE), hub, p -> false);
        }
        double usPerWalk = (System.nanoTime() - t0) / 1.0e3 / walks;
        System.out.printf("hub rediscovery, full bus: %.2f us per walk (one per hub per second)%n", usPerWalk);
        assertTrue(usPerWalk < 1_000, usPerWalk + " us");
    }
}
