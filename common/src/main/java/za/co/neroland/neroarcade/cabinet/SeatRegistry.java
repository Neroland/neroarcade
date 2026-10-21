package za.co.neroland.neroarcade.cabinet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One seat per player, server-wide (003 §2.2): player UUID → the seat holder (cabinet or bench) they sit at.
 * Runtime only — never saved, exported or logged; cleared when the server stops, and a player's entry goes when
 * they log out or are unseated.
 *
 * <p>Server thread only. {@link #SERVER} is the live instance; tests build their own.
 */
public final class SeatRegistry {

    /** Where a seat is: the dimension id and the holder's packed block position. No player data. */
    public record SeatKey(String dimension, long pos) {
    }

    public static final SeatRegistry SERVER = new SeatRegistry();

    private final Map<UUID, SeatKey> seats = new HashMap<>();

    public SeatRegistry() {
    }

    /** Claims a seat for {@code player}; false (and nothing changes) if they already sit anywhere else. */
    public boolean claim(UUID player, SeatKey at) {
        SeatKey current = seats.get(player);
        if (current != null) {
            return current.equals(at);
        }
        seats.put(player, at);
        return true;
    }

    /** Releases {@code player}'s seat if it is at {@code at}; a no-op otherwise. */
    public void release(UUID player, SeatKey at) {
        seats.remove(player, at);
    }

    public Optional<SeatKey> seatOf(UUID player) {
        return Optional.ofNullable(seats.get(player));
    }

    public boolean isSeated(UUID player) {
        return seats.containsKey(player);
    }

    /** Forgets {@code player} wherever they sat (logout, erasure). */
    public Optional<SeatKey> forget(UUID player) {
        return Optional.ofNullable(seats.remove(player));
    }

    public int size() {
        return seats.size();
    }

    public void clear() {
        seats.clear();
    }
}
