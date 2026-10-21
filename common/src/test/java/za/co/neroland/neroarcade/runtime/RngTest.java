package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** SplitMix64 reference vectors (computed independently with an arbitrary-precision implementation). */
class RngTest {

    private static long[] first10(long seed) {
        Rng rng = new Rng(seed);
        long[] out = new long[10];
        for (int i = 0; i < out.length; i++) {
            out[i] = rng.nextLong();
        }
        return out;
    }

    @Test
    void seedZeroVectors() {
        assertArrayEquals(new long[] {0xE220A8397B1DCDAFL, 0x6E789E6AA1B965F4L, 0x06C45D188009454FL,
                0xF88BB8A8724C81ECL, 0x1B39896A51A8749BL, 0x53CB9F0C747EA2EAL, 0x2C829ABE1F4532E1L,
                0xC584133AC916AB3CL, 0x3EE5789041C98AC3L, 0xF3B8488C368CB0A6L}, first10(0L));
    }

    @Test
    void seedMinusOneVectors() {
        assertArrayEquals(new long[] {0xE4D971771B652C20L, 0xE99FF867DBF682C9L, 0x382FF84CB27281E9L,
                0x6D1DB36CCBA982D2L, 0xB4A0472E578069AEL, 0xD31DADBDA438BB33L, 0xF14F2CF802083FA5L,
                0x405DA438A39E8064L, 0xC4FEA708156E0C84L, 0x031E50FE7BBD6E1CL}, first10(-1L));
    }

    @Test
    void boundsAreRespected() {
        Rng rng = new Rng(7L);
        for (int i = 0; i < 10_000; i++) {
            int a = rng.nextInt(13);
            assertTrue(a >= 0 && a < 13);
            int b = rng.nextInt(-5, 5);
            assertTrue(b >= -5 && b <= 5);
        }
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
        assertTrue(new Rng(1L).chance(1, 1));
        assertTrue(!new Rng(1L).chance(0, 5));
    }

    @Test
    void stateRoundTripsAndViewsShareTheField() {
        Rng a = new Rng(99L);
        a.nextLong();
        Rng b = new Rng(0L);
        b.setState(a.state());
        assertEquals(a.nextLong(), b.nextLong());
        MatchState state = new MatchState(4, 4, 1, 1);
        Rng view = Rng.view(state);
        view.setState(5L);
        assertEquals(5L, state.rngState());
        long next = view.nextLong();
        Rng standalone = new Rng(5L);
        assertEquals(standalone.nextLong(), next);
        assertEquals(standalone.state(), state.rngState());
        assertTrue(view.isViewOf(state));
    }
}
