package za.co.neroland.neroarcade.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Review 003b F1: the bus's host and seat indexes are sticky. Placing or breaking a block beside a running bus
 * never re-indexes a seated player, a restart plus an insert never steals the host, and linking a hub to a
 * stranger's running solo cabinet never ends their match. Pure ({@link BusAssignment}, {@link BusTopology}).
 */
class BusStickyAssignmentTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;
    private static final long D = 4L;
    private static final long NEWCOMER = 9L;

    private static BusAssignment.Member host(long key, long insertedAt, boolean running) {
        return new BusAssignment.Member(key, true, true, insertedAt, running, true);
    }

    private static BusAssignment.Member node(long key, boolean seated) {
        return new BusAssignment.Member(key, false, false, Long.MAX_VALUE, false, seated);
    }

    private static BusElection.Part part(BusElection.Role role, int seat) {
        return new BusElection.Part(role, seat);
    }

    @Test
    void aCabinetAddedMidMatchReIndexesNobody() {
        BusAssignment bus = new BusAssignment();
        bus.assign(List.of(host(A, 10, false), node(B, true), node(C, true)));
        // the match starts; a griefer places a cabinet nearer the hub, so discovery now finds it first
        BusElection.Part[] parts = bus.assign(List.of(node(NEWCOMER, false), host(A, 10, true), node(B, true),
                node(C, true)));
        assertEquals(part(BusElection.Role.NODE, 3), parts[0], "the newcomer takes the lowest free index");
        assertEquals(part(BusElection.Role.HOST, 0), parts[1]);
        assertEquals(part(BusElection.Role.NODE, 1), parts[2], "seated players keep their seats");
        assertEquals(part(BusElection.Role.NODE, 2), parts[3]);
    }

    @Test
    void breakingAMiddleNodeLeavesTheOthersWhereTheyAre() {
        BusAssignment bus = new BusAssignment();
        bus.assign(List.of(host(A, 10, false), node(B, true), node(C, true), node(D, true)));
        BusElection.Part[] parts = bus.assign(List.of(host(A, 10, true), node(C, true), node(D, true)));
        assertEquals(part(BusElection.Role.NODE, 2), parts[1]);
        assertEquals(part(BusElection.Role.NODE, 3), parts[2]);
        assertEquals(4, BusAssignment.slots(parts), "the gap is kept: seat 3 is still in range");
        assertEquals(3, BusAssignment.present(parts), "but only three cabinets can seat (the lobby minimum)");
    }

    @Test
    void unseatedNodesCompactWhenNoMatchRuns() {
        BusAssignment bus = new BusAssignment();
        bus.assign(List.of(host(A, 10, false), node(B, false), node(C, true), node(D, false)));
        BusElection.Part[] parts = bus.assign(List.of(host(A, 10, false), node(C, true), node(D, false)));
        assertEquals(part(BusElection.Role.NODE, 2), parts[1], "a seated node keeps its index");
        assertEquals(part(BusElection.Role.NODE, 1), parts[2], "an unseated node fills the hole");
        assertEquals(3, BusAssignment.slots(parts));
    }

    @Test
    void afterARestartAnInsertIntoANodeNeverStealsTheHost() {
        // a cartridge loaded from disk reads insertedAt = Long.MIN_VALUE; a live insert reads the game time
        BusAssignment bus = new BusAssignment();
        BusElection.Part[] parts = bus.assign(List.of(host(A, Long.MIN_VALUE, false), node(B, false)));
        assertEquals(BusElection.Role.HOST, parts[0].role());
        parts = bus.assign(List.of(host(A, Long.MIN_VALUE, false), host(B, 500, false)));
        assertEquals(BusElection.Role.HOST, parts[0].role(), "the incumbent keeps the bus");
        assertEquals(BusElection.Role.CONFLICT, parts[1].role(), "the second cartridge is locked");
        // even a fresh hub (no incumbent) elects the loaded cartridge
        parts = new BusAssignment().assign(List.of(host(B, 500, false), host(A, Long.MIN_VALUE, false)));
        assertEquals(BusElection.Role.HOST, parts[1].role());
    }

    @Test
    void theIncumbentHostStaysEvenAgainstAnEarlierInsert() {
        BusAssignment bus = new BusAssignment();
        bus.assign(List.of(host(A, 500, false), node(B, false)));
        BusElection.Part[] parts = bus.assign(List.of(host(A, 500, false), host(B, 100, false)));
        assertEquals(BusElection.Role.HOST, parts[0].role());
        assertEquals(BusElection.Role.CONFLICT, parts[1].role());
    }

    @Test
    void aSoloCabinetLinkedMidMatchKeepsPlaying() {
        BusAssignment bus = new BusAssignment();
        // the hub's own cabinet holds an earlier cartridge; the stranger's solo match is running
        BusElection.Part[] parts = bus.assign(List.of(host(B, 100, false), host(A, 900, true)));
        assertEquals(BusElection.Role.HOST, parts[0].role());
        assertNull(parts[1], "deferred: left alone until its own match ends");
        // its match ended: now it is locked like any second cartridge
        parts = bus.assign(List.of(host(B, 100, false), host(A, 900, false)));
        assertEquals(BusElection.Role.CONFLICT, parts[1].role());
    }

    @Test
    void aRunningSoloCabinetAloneOnANewBusBecomesItsHost() {
        BusElection.Part[] parts = new BusAssignment().assign(List.of(node(B, false), host(A, 900, true)));
        assertEquals(part(BusElection.Role.HOST, 0), parts[1], "solo to host keeps the running match");
        assertEquals(part(BusElection.Role.NODE, 1), parts[0]);
    }

    @Test
    void aNewcomerNeverPushesAMemberIntoOverflow() {
        List<BlockPos> discovered = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            discovered.add(new BlockPos(i, 64, 0));
        }
        // the newcomer (index 0) was found first, so the cap put the last member into overflow
        BusTopology t = new BusTopology(0L, BlockPos.ZERO, discovered.subList(0, 4), List.of(), List.of(),
                List.of(discovered.get(4)), List.of());
        BusTopology kept = t.keeping(discovered.subList(1, 5));
        assertEquals(discovered.subList(1, 5), kept.cabinets());
        assertEquals(List.of(discovered.get(0)), kept.overflow(), "the newcomer waits: BUS FULL");
    }
}
