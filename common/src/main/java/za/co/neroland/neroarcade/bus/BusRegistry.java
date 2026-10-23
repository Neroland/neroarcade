package za.co.neroland.neroarcade.bus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * The live Link Buses per dimension (003 §3.2–3.3): each hub's last {@link BusTopology}, which bus holds each
 * cabinet and marquee (one bus per cabinet: the first hub to claim it keeps it until it rediscovers without it),
 * and which buses must rediscover now because a block on or beside them changed. Runtime only — rebuilt by the
 * hubs from the world; cleared when the server stops. Server thread only.
 */
public final class BusRegistry {

    public static final BusRegistry SERVER = new BusRegistry();

    private static final int[][] AROUND = {
        {0, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private static final class Dim {
        final Map<Long, BusTopology> buses = new HashMap<>();
        /** Member position → bus (hub, cables, cabinets, marquees). */
        final Map<Long, Long> members = new HashMap<>();
        /** Cabinet / marquee position → the bus that holds it. */
        final Map<Long, Long> claims = new HashMap<>();
        final Set<Long> dirty = new HashSet<>();
    }

    private final Map<String, Dim> dims = new HashMap<>();

    private Dim dim(String dimension) {
        return dims.computeIfAbsent(dimension, k -> new Dim());
    }

    /** True when {@code pos} (a cabinet or marquee) belongs to a bus other than {@code busId}. */
    public boolean claimedElsewhere(String dimension, long pos, long busId) {
        Long holder = dim(dimension).claims.get(pos);
        return holder != null && holder != busId;
    }

    /** Replaces what {@code topology.busId()} holds. */
    public void put(String dimension, BusTopology topology) {
        Dim d = dim(dimension);
        forget(d, topology.busId());
        d.buses.put(topology.busId(), topology);
        for (long member : topology.members()) {
            d.members.put(member, topology.busId());
        }
        topology.cabinets().forEach(p -> d.claims.put(p.asLong(), topology.busId()));
        topology.marquees().forEach(p -> d.claims.put(p.asLong(), topology.busId()));
    }

    /** Drops a bus (hub broken or unloaded). @return what it held, or null */
    public @Nullable BusTopology remove(String dimension, long busId) {
        Dim d = dims.get(dimension);
        if (d == null) {
            return null;
        }
        BusTopology gone = d.buses.get(busId);
        forget(d, busId);
        d.dirty.remove(busId);
        return gone;
    }

    private static void forget(Dim d, long busId) {
        BusTopology old = d.buses.remove(busId);
        if (old == null) {
            return;
        }
        d.members.values().removeIf(id -> id == busId);
        d.claims.values().removeIf(id -> id == busId);
    }

    public @Nullable BusTopology get(String dimension, long busId) {
        Dim d = dims.get(dimension);
        return d == null ? null : d.buses.get(busId);
    }

    /** The bus a cabinet or marquee belongs to, or 0. */
    public long busOf(String dimension, long pos) {
        Dim d = dims.get(dimension);
        Long id = d == null ? null : d.claims.get(pos);
        return id == null ? 0L : id;
    }

    /**
     * A bus, cable, cabinet or marquee was placed, removed or loaded at {@code pos}: every bus that holds that
     * position or one beside it rediscovers on its hub's next tick.
     */
    public void touch(String dimension, BlockPos pos) {
        Dim d = dims.get(dimension);
        if (d == null) {
            return;
        }
        for (int[] o : AROUND) {
            Long bus = d.members.get(BlockPos.asLong(pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2]));
            if (bus != null) {
                d.dirty.add(bus);
            }
        }
    }

    public void markDirty(String dimension, long busId) {
        if (busId != 0) {
            dim(dimension).dirty.add(busId);
        }
    }

    /** True once after a {@link #touch}/{@link #markDirty} for this bus. */
    public boolean consumeDirty(String dimension, long busId) {
        Dim d = dims.get(dimension);
        return d != null && d.dirty.remove(busId);
    }

    public int busCount() {
        int n = 0;
        for (Dim d : dims.values()) {
            n += d.buses.size();
        }
        return n;
    }

    public void clear() {
        dims.clear();
    }
}
