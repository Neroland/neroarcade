package za.co.neroland.neroarcade.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/** 003 §3.2: discovery limits, loops, one bus per cabinet, host election. Pure (a map stands in for the world). */
class BusDiscoveryTest {

    private static final BlockPos HUB = new BlockPos(0, 64, 0);

    private static final class World implements BusWorld {
        final Map<Long, BusNode> nodes = new HashMap<>();

        World put(int x, int y, int z, BusNode node) {
            nodes.put(BlockPos.asLong(x, y, z), node);
            return this;
        }

        @Override
        public BusNode nodeAt(BlockPos pos) {
            return nodes.getOrDefault(pos.asLong(), BusNode.NONE);
        }
    }

    /** A hub at the origin with a straight cable run east of {@code length} cables. */
    private static World line(int length) {
        World w = new World().put(0, 64, 0, BusNode.HUB);
        for (int i = 1; i <= length; i++) {
            w.put(i, 64, 0, BusNode.CABLE);
        }
        return w;
    }

    @Test
    void sixteenCablesAreWalkedTheSeventeenthIsNot() {
        World w = line(20);
        w.put(16, 65, 0, BusNode.CABINET); // beside cable 16: reached
        w.put(17, 65, 0, BusNode.CABINET); // beside cable 17: not reached
        BusTopology t = BusTopology.discover(w, HUB, pos -> false);
        assertEquals(BusTopology.MAX_CABLES, t.cables().size());
        assertEquals(List.of(new BlockPos(16, 65, 0)), t.cabinets());
    }

    @Test
    void aFifthCabinetOverflows() {
        World w = line(5);
        for (int i = 1; i <= 5; i++) {
            w.put(i, 65, 0, BusNode.CABINET);
        }
        BusTopology t = BusTopology.discover(w, HUB, pos -> false);
        assertEquals(BusTopology.MAX_CABINETS, t.cabinets().size());
        assertEquals(List.of(new BlockPos(5, 65, 0)), t.overflow());
        assertEquals(0, t.indexOf(new BlockPos(1, 65, 0)), "discovery order is the seat order");
    }

    @Test
    void cableLoopsTerminate() {
        World w = new World().put(0, 64, 0, BusNode.HUB);
        // a 3x3 ring of cables around (2, 64, 2), plus a filled 4x4 slab to make many cycles
        for (int x = 1; x <= 4; x++) {
            for (int z = -1; z <= 2; z++) {
                w.put(x, 64, z, BusNode.CABLE);
            }
        }
        w.put(1, 65, 0, BusNode.CABINET);
        BusTopology t = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> BusTopology.discover(w, HUB, pos -> false));
        assertTrue(t.cables().size() <= BusTopology.MAX_CABLES);
        assertEquals(1, t.cabinets().size());
    }

    @Test
    void anotherHubIsNeverWalkedIntoSoBusesDoNotMerge() {
        World w = line(4).put(5, 64, 0, BusNode.HUB);
        for (int i = 6; i <= 8; i++) {
            w.put(i, 64, 0, BusNode.CABLE);
        }
        w.put(8, 65, 0, BusNode.CABINET); // only reachable through the second hub
        BusTopology t = BusTopology.discover(w, HUB, pos -> false);
        assertEquals(4, t.cables().size());
        assertTrue(t.cabinets().isEmpty());
    }

    @Test
    void oneBusPerCabinetTheFirstClaimKeepsIt() {
        // two hubs share one cabinet between them
        World w = new World().put(0, 64, 0, BusNode.HUB).put(1, 64, 0, BusNode.CABLE)
                .put(2, 64, 0, BusNode.CABINET).put(3, 64, 0, BusNode.CABLE).put(4, 64, 0, BusNode.HUB);
        BusRegistry registry = new BusRegistry();
        BlockPos second = new BlockPos(4, 64, 0);
        BusTopology first = BusTopology.discover(w, HUB, p -> registry.claimedElsewhere("o", p, HUB.asLong()));
        registry.put("o", first);
        BusTopology other = BusTopology.discover(w, second,
                p -> registry.claimedElsewhere("o", p, second.asLong()));
        registry.put("o", other);
        assertEquals(List.of(new BlockPos(2, 64, 0)), first.cabinets());
        assertTrue(other.cabinets().isEmpty());
        assertEquals(List.of(new BlockPos(2, 64, 0)), other.conflicts());
        assertEquals(HUB.asLong(), registry.busOf("o", new BlockPos(2, 64, 0).asLong()));
        // the first hub rediscovering keeps its claim
        assertFalse(registry.claimedElsewhere("o", new BlockPos(2, 64, 0).asLong(), HUB.asLong()));
    }

    @Test
    void touchMarksTheBusBesideAChangedBlock() {
        World w = line(3);
        BusRegistry registry = new BusRegistry();
        registry.put("o", BusTopology.discover(w, HUB, p -> false));
        registry.touch("o", new BlockPos(3, 65, 0)); // beside cable 3
        assertTrue(registry.consumeDirty("o", HUB.asLong()));
        assertFalse(registry.consumeDirty("o", HUB.asLong()), "consumed once");
        registry.touch("o", new BlockPos(30, 64, 0));
        assertFalse(registry.consumeDirty("o", HUB.asLong()));
    }

    @Test
    void hostElectionFirstInsertedWinsTheOtherIsLocked() {
        List<BusElection.Candidate> cabinets = List.of(
                new BusElection.Candidate(false, false, Long.MAX_VALUE), // empty
                new BusElection.Candidate(true, true, 500L),              // second cartridge in
                new BusElection.Candidate(true, true, 200L),              // first cartridge in: host
                new BusElection.Candidate(false, false, Long.MAX_VALUE)); // empty
        BusElection.Part[] parts = BusElection.parts(cabinets);
        assertEquals(BusElection.Role.NODE, parts[0].role());
        assertEquals(BusElection.Role.CONFLICT, parts[1].role());
        assertEquals(-1, parts[1].seat());
        assertEquals(BusElection.Role.HOST, parts[2].role());
        assertEquals(0, parts[2].seat(), "the host is seat 0");
        assertEquals(1, parts[0].seat(), "nodes in discovery order after the host");
        assertEquals(2, parts[3].seat());
    }

    @Test
    void anUnreadableCartridgeNeverHostsAndNoHostMeansWaiting() {
        BusElection.Part[] parts = BusElection.parts(List.of(
                new BusElection.Candidate(true, false, 1L),
                new BusElection.Candidate(false, false, Long.MAX_VALUE)));
        assertEquals(-1, BusElection.host(List.of(new BusElection.Candidate(true, false, 1L))));
        assertEquals(BusElection.Role.CONFLICT, parts[0].role());
        assertEquals(BusElection.Role.NODE_NO_HOST, parts[1].role());
    }
}
