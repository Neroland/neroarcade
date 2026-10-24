package za.co.neroland.neroarcade.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.cabinet.BusPhase;

/** The status strip: a cabinet holding a readable cartridge reads READY even while its host sleeps (004 A3). */
class CabinetStatusTest {

    @Test
    void offOnlyWithoutACartridge() {
        assertEquals(CabinetStatus.OFF, CabinetStatus.of(false, false, true, null));
        assertEquals(CabinetStatus.READY, CabinetStatus.of(true, true, true, null), "asleep but ready: attract loop");
        assertEquals(CabinetStatus.LOCKED, CabinetStatus.of(true, false, true, null));
        assertEquals(CabinetStatus.NO_POWER, CabinetStatus.of(true, true, false, null));
    }

    @Test
    void lightFollowsTheStripButNotWithoutPower() {
        // Stage 5 review F4 / 004 A10: 7 for every status but OFF and NO POWER
        assertEquals(0, CabinetStatus.OFF.blockLight());
        assertEquals(0, CabinetStatus.NO_POWER.blockLight(), "an unpowered cabinet does not glow");
        assertEquals(7, CabinetStatus.READY.blockLight());
        assertEquals(7, CabinetStatus.PLAYING.blockLight());
        assertEquals(7, CabinetStatus.LINKED.blockLight());
        assertEquals(7, CabinetStatus.LOCKED.blockLight());
    }

    @Test
    void anAwakeHostReportsItsPhase() {
        assertEquals(CabinetStatus.READY, CabinetStatus.of(true, true, true, BusPhase.LOBBY));
        assertEquals(CabinetStatus.PLAYING, CabinetStatus.of(true, true, true, BusPhase.COUNTDOWN));
        assertEquals(CabinetStatus.PLAYING, CabinetStatus.of(true, true, true, BusPhase.PLAYING));
        assertEquals(CabinetStatus.PLAYING, CabinetStatus.of(true, true, true, BusPhase.RESULTS));
    }
}
