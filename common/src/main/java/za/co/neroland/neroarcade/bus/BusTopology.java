package za.co.neroland.neroarcade.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

import net.minecraft.core.BlockPos;

/**
 * One Link Bus as discovery found it (003 §3.2): a breadth-first walk from the hub through Link Cable blocks that
 * collects adjacent cabinets (lower halves) and Marquee Screens. Pure — the world is a {@link BusWorld} — so the
 * limits are unit-tested.
 *
 * <ul>
 *   <li><b>≤ {@value #MAX_CABLES} cables</b>: the 17th is not walked (it stays dark).</li>
 *   <li><b>≤ {@value #MAX_CABINETS} cabinets</b>: a 5th goes to {@link #overflow} ("BUS FULL").</li>
 *   <li><b>One bus per cabinet</b>: a cabinet another bus already holds goes to {@link #conflicts} and is
 *       skipped.</li>
 *   <li>Another hub is never walked into, so two hubs never merge; every position is visited once, so cable loops
 *       end. Neighbours are scanned in a fixed order (down, up, north, south, west, east): discovery order is
 *       deterministic, and it is the seat order.</li>
 * </ul>
 *
 * @param busId     the hub's {@code BlockPos.asLong()} — stable, needs no saved data
 * @param cabinets  lower halves, discovery order
 * @param marquees  marquee screens, discovery order (≤ {@value #MAX_MARQUEES})
 * @param cables    cable positions walked
 * @param overflow  cabinets past the cap
 * @param conflicts cabinets claimed by another bus
 */
public record BusTopology(long busId, BlockPos busPos, List<BlockPos> cabinets, List<BlockPos> marquees,
        List<BlockPos> cables, List<BlockPos> overflow, List<BlockPos> conflicts) {

    public static final int MAX_CABLES = 16;
    public static final int MAX_CABINETS = 4;
    public static final int MAX_MARQUEES = 4;

    /** Neighbour offsets in {@code Direction} order: down, up, north, south, west, east. */
    private static final int[][] OFFSETS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public BusTopology {
        busPos = busPos.immutable();
        cabinets = List.copyOf(cabinets);
        marquees = List.copyOf(marquees);
        cables = List.copyOf(cables);
        overflow = List.copyOf(overflow);
        conflicts = List.copyOf(conflicts);
    }

    /** An empty bus (multiplayer disabled, or nothing attached). */
    public static BusTopology empty(BlockPos hub) {
        return new BusTopology(hub.asLong(), hub, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Walks from {@code hub}.
     *
     * @param claimedElsewhere true for a cabinet or marquee position another bus already holds
     */
    public static BusTopology discover(BusWorld world, BlockPos hub, LongPredicate claimedElsewhere) {
        BlockPos origin = hub.immutable();
        List<BlockPos> cabinets = new ArrayList<>();
        List<BlockPos> marquees = new ArrayList<>();
        List<BlockPos> cables = new ArrayList<>();
        List<BlockPos> overflow = new ArrayList<>();
        List<BlockPos> conflicts = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin.asLong());
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos at = queue.removeFirst();
            for (int[] o : OFFSETS) {
                BlockPos next = at.offset(o[0], o[1], o[2]);
                if (!visited.add(next.asLong())) {
                    continue;
                }
                switch (world.nodeAt(next)) {
                    case CABLE -> {
                        if (cables.size() < MAX_CABLES) {
                            cables.add(next);
                            queue.addLast(next);
                        }
                    }
                    case CABINET -> {
                        if (claimedElsewhere != null && claimedElsewhere.test(next.asLong())) {
                            conflicts.add(next);
                        } else if (cabinets.size() < MAX_CABINETS) {
                            cabinets.add(next);
                        } else {
                            overflow.add(next);
                        }
                    }
                    case MARQUEE -> {
                        boolean taken = claimedElsewhere != null && claimedElsewhere.test(next.asLong());
                        if (!taken && marquees.size() < MAX_MARQUEES) {
                            marquees.add(next);
                        }
                    }
                    default -> {
                        // NONE and other hubs end the walk here
                    }
                }
            }
        }
        return new BusTopology(origin.asLong(), origin, cabinets, marquees, cables, overflow, conflicts);
    }

    /** Every position this bus holds (hub, cables, cabinets, marquees) as packed longs. */
    public Set<Long> members() {
        Set<Long> out = new LinkedHashSet<>();
        out.add(busId);
        cables.forEach(p -> out.add(p.asLong()));
        cabinets.forEach(p -> out.add(p.asLong()));
        marquees.forEach(p -> out.add(p.asLong()));
        return out;
    }

    /** The cabinet's index on this bus, or -1. */
    public int indexOf(BlockPos cabinet) {
        return cabinets.indexOf(cabinet);
    }
}
