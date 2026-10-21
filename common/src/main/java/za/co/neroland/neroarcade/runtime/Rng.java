package za.co.neroland.neroarcade.runtime;

/**
 * The match RNG: SplitMix64 on one {@code long} of state, using only 64-bit add/xor/shift/multiply, so it is
 * identical on every JVM and loader and snapshots in 8 bytes.
 *
 * <p><b>Invariants:</b> the only randomness a hook may use. A runtime's RNG is a <em>view</em> over
 * {@code MatchState.rngState} ({@link #view(MatchState)}), so a keyframe and the state hash always cover it.
 * {@code nextInt(bound)} uses {@code remainderUnsigned}; its modulo bias is below 2^-32 for any int bound and
 * accepted, because a rejection loop would make the number of draws data-dependent.
 */
public final class Rng {

    private final MatchState backing;
    private long s;

    /** A standalone generator (tests, input scripts). */
    public Rng(long seed) {
        this.backing = null;
        this.s = seed;
    }

    private Rng(MatchState backing) {
        this.backing = backing;
    }

    /** A generator whose state <em>is</em> {@code state.rngState}. */
    public static Rng view(MatchState state) {
        return new Rng(state);
    }

    /** True when this generator reads and writes {@code state}'s RNG field. */
    public boolean isViewOf(MatchState state) {
        return backing == state;
    }

    public long nextLong() {
        long z = state() + 0x9E3779B97F4A7C15L;
        setState(z);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    public int nextInt(int min, int maxInclusive) {
        return min + nextInt(maxInclusive - min + 1);
    }

    public boolean chance(int numerator, int denominator) {
        return nextInt(denominator) < numerator;
    }

    public long state() {
        return backing != null ? backing.rngState : s;
    }

    public void setState(long state) {
        if (backing != null) {
            backing.rngState = state;
        } else {
            s = state;
        }
    }
}
