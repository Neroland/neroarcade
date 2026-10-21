package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/** 003 §2.2: one seat per player server-wide; a cabinet holds one seat; re-use of your own seat is a no-op. */
class SeatExclusivityTest {

    private static final SeatRegistry.SeatKey CABINET_A = new SeatRegistry.SeatKey("minecraft:overworld", 1L);
    private static final SeatRegistry.SeatKey CABINET_B = new SeatRegistry.SeatKey("minecraft:overworld", 2L);

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void onePlayerOneSeat() {
        SeatRegistry registry = new SeatRegistry();
        assertTrue(registry.claim(HostFixtures.ALICE, CABINET_A));
        assertTrue(registry.claim(HostFixtures.ALICE, CABINET_A), "the same seat again is a no-op");
        assertFalse(registry.claim(HostFixtures.ALICE, CABINET_B), "a second seat elsewhere is refused");
        registry.release(HostFixtures.ALICE, CABINET_B);
        assertTrue(registry.isSeated(HostFixtures.ALICE), "releasing a seat you do not hold changes nothing");
        registry.release(HostFixtures.ALICE, CABINET_A);
        assertTrue(registry.claim(HostFixtures.ALICE, CABINET_B));
        assertEquals(CABINET_B, registry.forget(HostFixtures.ALICE).orElseThrow());
        assertEquals(0, registry.size());
    }

    @Test
    void aCabinetHoldsOneSeat() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        assertTrue(host.bind(0, new Seat(0, HostFixtures.ALICE, 0)));
        assertFalse(host.bind(0, new Seat(0, HostFixtures.BOB, 0)));
        assertFalse(host.bind(1, new Seat(1, HostFixtures.BOB, 0)), "a solo bus has one seat");
    }
}
