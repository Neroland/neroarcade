package za.co.neroland.neroarcade.bus;

import java.util.List;

/**
 * Host election on a bus (003 §3.2), pure. The host is the cabinet holding a <b>readable</b> cartridge that was
 * inserted first ({@code insertedAt}, runtime-only game time; after a reload every cabinet reads the same value, so
 * discovery order decides). Every other cabinet holding a cartridge is locked ("LINKED — REMOVE CARTRIDGE") and
 * takes no seat. Cabinets without a cartridge are nodes; seat indexes are handed out host first, then nodes in
 * discovery order.
 */
public final class BusElection {

    /** What election needs from one cabinet, in discovery order. */
    public record Candidate(boolean hasCartridge, boolean readable, long insertedAt) {
    }

    /** One cabinet's part: {@code seat} is its bus seat index (-1 for a locked cabinet). */
    public record Part(Role role, int seat) {
    }

    public enum Role {
        HOST,
        NODE,
        /** A node on a bus with no host: "LINKED — WAITING". */
        NODE_NO_HOST,
        /** Holds a cartridge but is not the host. */
        CONFLICT
    }

    private BusElection() {
    }

    /** Index of the host candidate, or -1. */
    public static int host(List<Candidate> candidates) {
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.hasCartridge() && c.readable()
                    && (best < 0 || c.insertedAt() < candidates.get(best).insertedAt())) {
                best = i;
            }
        }
        return best;
    }

    /** Each candidate's role and seat. */
    public static Part[] parts(List<Candidate> candidates) {
        int host = host(candidates);
        Part[] out = new Part[candidates.size()];
        int next = 1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (i == host) {
                out[i] = new Part(Role.HOST, 0);
            } else if (c.hasCartridge()) {
                out[i] = new Part(Role.CONFLICT, -1);
            } else if (host < 0) {
                out[i] = new Part(Role.NODE_NO_HOST, -1);
            } else {
                out[i] = new Part(Role.NODE, next++);
            }
        }
        return out;
    }
}
