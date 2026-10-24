package za.co.neroland.neroarcade.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Sticky role and seat assignment for one bus (003 §3.2, review 003b F1), pure. The hub keeps one per bus and runs
 * it after every discovery; {@link BusElection} is only consulted when there is no incumbent host.
 *
 * <ul>
 *   <li><b>Incumbent host:</b> the cabinet that hosted last time stays the host while it is on the bus and still
 *       holds a readable cartridge, whatever its {@code insertedAt}. Election runs only without one.</li>
 *   <li><b>Sticky seats:</b> a node that holds a seat keeps its bus seat index; nodes with nobody seated fill the
 *       lowest free index 1..3 in discovery order (so the bus compacts when it can without moving a player);
 *       a newcomer takes the lowest free index. Placing a block beside the bus never re-indexes a seated
 *       player.</li>
 *   <li><b>Frozen while running:</b> while the incumbent host's match counts down or plays, every cabinet that was
 *       already on the bus keeps its part; only newcomers are assigned (removals are the hub's {@code leaveBus}).
 *       The hub re-runs this every second, so deferred changes land once the match ends.</li>
 *   <li><b>Deferred strangers:</b> a cabinet whose <i>own</i> match is running (a solo cabinet just linked to this
 *       bus) and that is not the host gets {@code null} — leave it as it is and ask again later — so linking a
 *       bus to a stranger's cabinet never ends their match.</li>
 * </ul>
 */
public final class BusAssignment {

    /** Bus seat indexes a node can hold (the host is 0). */
    static final int MAX_INDEX = BusTopology.MAX_CABINETS - 1;

    /**
     * What assignment needs from one cabinet, in discovery order.
     *
     * @param key          the cabinet's packed position
     * @param running      its own {@code MatchHost} counts down or plays (a host or solo cabinet)
     * @param seated       a player sits at it
     */
    public record Member(long key, boolean hasCartridge, boolean readable, long insertedAt, boolean running,
            boolean seated) {
    }

    private final Map<Long, BusElection.Part> last = new HashMap<>();
    private @Nullable Long hostKey;

    /** The incumbent host's key, or null. */
    public @Nullable Long hostKey() {
        return hostKey;
    }

    /** Forget everything (the hub was dissolved). */
    public void clear() {
        last.clear();
        hostKey = null;
    }

    /**
     * Each member's part, or {@code null} for "unchanged — leave it alone this round".
     */
    public BusElection.Part[] assign(List<Member> members) {
        Set<Long> present = new HashSet<>();
        for (Member m : members) {
            present.add(m.key());
        }
        last.keySet().retainAll(present);

        int host = -1;
        boolean incumbent = false;
        if (hostKey != null) {
            for (int i = 0; i < members.size(); i++) {
                Member m = members.get(i);
                if (m.key() == hostKey && m.hasCartridge() && m.readable()) {
                    host = i;
                    incumbent = true;
                }
            }
        }
        if (host < 0) {
            List<BusElection.Candidate> candidates = new ArrayList<>();
            for (Member m : members) {
                // a stranger with its own running match is deferred, never elected over the bus
                candidates.add(new BusElection.Candidate(m.hasCartridge(), m.readable() && !m.running(),
                        m.insertedAt()));
            }
            host = BusElection.host(candidates);
            if (host < 0) {
                // only running strangers hold readable cartridges: the first of them may host after all
                for (int i = 0; i < members.size() && host < 0; i++) {
                    if (members.get(i).hasCartridge() && members.get(i).readable()) {
                        host = i;
                    }
                }
            }
        }
        hostKey = host < 0 ? null : members.get(host).key();
        boolean frozen = incumbent && members.get(host).running();

        BusElection.Part[] out = new BusElection.Part[members.size()];
        boolean[] decided = new boolean[members.size()];
        Set<Integer> used = new HashSet<>();
        if (host >= 0) {
            out[host] = new BusElection.Part(BusElection.Role.HOST, 0);
            decided[host] = true;
            used.add(0);
        }
        // pass 1: what stays as it is
        for (int i = 0; i < members.size(); i++) {
            if (decided[i]) {
                continue;
            }
            Member m = members.get(i);
            BusElection.Part prev = last.get(m.key());
            if (prev != null && prev.role() == BusElection.Role.HOST) {
                prev = null; // a former host is a newcomer in its new part
            }
            if (m.running()) {
                out[i] = null; // a stranger's own match: deferred
                decided[i] = true;
            } else if (frozen && prev != null) {
                out[i] = prev;
                decided[i] = true;
                if (prev.seat() > 0) {
                    used.add(prev.seat());
                }
            } else if (m.hasCartridge()) {
                out[i] = new BusElection.Part(BusElection.Role.CONFLICT, -1);
                decided[i] = true;
            } else if (host < 0) {
                out[i] = new BusElection.Part(BusElection.Role.NODE_NO_HOST, -1);
                decided[i] = true;
            } else if (m.seated() && prev != null && prev.role() == BusElection.Role.NODE && prev.seat() > 0
                    && prev.seat() <= MAX_INDEX && used.add(prev.seat())) {
                out[i] = prev;
                decided[i] = true;
            }
        }
        // pass 2: unseated nodes and newcomers fill the lowest free index, in discovery order
        for (int i = 0; i < members.size(); i++) {
            if (decided[i]) {
                continue;
            }
            int index = -1;
            for (int s = 1; s <= MAX_INDEX; s++) {
                if (!used.contains(s)) {
                    index = s;
                    break;
                }
            }
            if (index < 0) {
                out[i] = new BusElection.Part(BusElection.Role.NODE_NO_HOST, -1); // cannot happen with ≤ 4 cabinets
            } else {
                used.add(index);
                out[i] = new BusElection.Part(BusElection.Role.NODE, index);
            }
        }
        for (int i = 0; i < members.size(); i++) {
            if (out[i] != null) {
                last.put(members.get(i).key(), out[i]);
            } else {
                last.remove(members.get(i).key());
            }
        }
        return out;
    }

    /** Seat slots the host needs: the highest assigned index + 1 (≥ 1); a gap left by a seated node counts. */
    public static int slots(BusElection.Part[] parts) {
        int max = -1;
        for (BusElection.Part p : parts) {
            if (p != null && p.seat() > max) {
                max = p.seat();
            }
        }
        return Math.max(1, max + 1);
    }

    /** Cabinets that can seat a player (host + nodes). */
    public static int present(BusElection.Part[] parts) {
        int n = 0;
        for (BusElection.Part p : parts) {
            if (p != null && p.seat() >= 0) {
                n++;
            }
        }
        return n;
    }
}
