package za.co.neroland.neroarcade.runtime;

import java.util.function.IntConsumer;

/**
 * FNV-1a 64 over a match state's canonical serialisation — exactly the bytes
 * {@link MatchState#writeKeyframe} produces — fed straight into the accumulator without building the array.
 *
 * <p><b>Invariant:</b> two states hash equal iff their keyframes are byte-identical. The hash covers the RNG
 * state, tick, phase, cells, every entity field and aux, the seat arrays, timer, winners and result; it
 * excludes the seat layout (derived from the cartridge) — and, by the keyframe format, nextEntityId.
 */
public final class StateHash {

    public static final long OFFSET = 0xcbf29ce484222325L;
    public static final long PRIME = 0x100000001b3L;

    private StateHash() {
    }

    public static long of(MatchState state) {
        Fnv fnv = new Fnv();
        state.writeKeyframe(fnv);
        return fnv.hash;
    }

    /** Hash of what a viewer can see: RNG state and status countdowns zeroed; optionally the tick too. */
    public static long ofView(MatchState state, boolean ignoreTick) {
        Fnv fnv = new Fnv();
        state.writeKeyframe(fnv, true, ignoreTick);
        return fnv.hash;
    }

    /** FNV-1a 64 of a byte array (tests compare it with {@link #of}). */
    public static long of(byte[] bytes) {
        Fnv fnv = new Fnv();
        for (byte b : bytes) {
            fnv.accept(b);
        }
        return fnv.hash;
    }

    private static final class Fnv implements IntConsumer {
        private long hash = OFFSET;

        @Override
        public void accept(int b) {
            hash = (hash ^ (b & 0xFF)) * PRIME;
        }
    }
}
