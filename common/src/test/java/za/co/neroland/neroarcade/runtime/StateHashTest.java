package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

class StateHashTest {

    /** {@link MatchStateTest#sample()}'s hash. A change means the canonical serialisation changed. */
    static final long SAMPLE_HASH = 0x9C8AC1DC3BDE36F5L;

    @Test
    void fnvReferenceVectors() {
        assertEquals(0xcbf29ce484222325L, StateHash.of(new byte[0]));
        assertEquals(0xaf63dc4c8601ec8cL, StateHash.of(new byte[] {'a'}));
        assertEquals(0x85944171f73967e8L, StateHash.of("foobar".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    @Test
    void hashIsFnvOfTheKeyframeBytes() {
        MatchState s = MatchStateTest.sample();
        assertEquals(StateHash.of(SnapshotCodec.writeKeyframe(s)), s.stateHash());
    }

    @Test
    void hashCoversRngTickAndAux() {
        MatchState s = MatchStateTest.sample();
        long base = s.stateHash();
        MatchState rng = s.copy();
        rng.rngState++;
        MatchState tick = s.copy();
        tick.tick++;
        MatchState aux = s.copy();
        aux.setAux(aux.entityIdAt(1), 7);
        assertNotEquals(base, rng.stateHash());
        assertNotEquals(base, tick.stateHash());
        assertNotEquals(base, aux.stateHash());
        assertEquals(StateHash.ofView(s, false), StateHash.ofView(rng, false), "view ignores the RNG");
    }
}
