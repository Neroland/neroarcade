package za.co.neroland.neroarcade.cabinet;

import za.co.neroland.neroarcade.bus.BusElection;

/**
 * What a cabinet is on its bus (003 §3). Runtime only; the hub assigns it on every discovery. The ordinal is the
 * update-tag value the client reads: append only.
 */
public enum BusRole {
    /** No bus: its own host on the loopback bus. */
    SOLO,
    /** Holds the bus's cartridge and runs its match. */
    HOST,
    /** A display node: mirrors the host and seats one player. */
    NODE,
    /** On a bus with no host: "LINKED — WAITING". */
    NODE_NO_HOST,
    /** The bus already has four cabinets: "BUS FULL". */
    FULL,
    /** Holds a cartridge on a bus that already has a host: "LINKED — REMOVE CARTRIDGE". */
    CONFLICT;

    public static BusRole of(BusElection.Role role) {
        return switch (role) {
            case HOST -> HOST;
            case NODE -> NODE;
            case NODE_NO_HOST -> NODE_NO_HOST;
            case CONFLICT -> CONFLICT;
        };
    }

    public static BusRole byOrdinal(int ordinal) {
        BusRole[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SOLO;
    }

    public boolean linked() {
        return this != SOLO;
    }
}
