package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/**
 * Review 003b F2/F3: nobody can insert a cartridge into a bus node or a cabinet someone else sits at, and nobody
 * but a player seated on the bus (or an op, checked by the caller) can eject the host cartridge while anyone sits
 * on the bus. Pure checks of {@link CabinetInteraction}.
 */
class CabinetInteractionTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void insertIntoASeatedNodeIsRefusedAndTheSeatStays() {
        Seat bob = new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE);
        assertEquals(CabinetInteraction.Refusal.TAKEN,
                CabinetInteraction.insertRefusal(bob, HostFixtures.CAROL, BusRole.NODE));
        assertEquals(HostFixtures.BOB, bob.playerUuid, "the seat is untouched");
    }

    @Test
    void insertIntoAnEmptyLinkedNodeOrAFullBusCabinetIsRefused() {
        assertEquals(CabinetInteraction.Refusal.LINKED,
                CabinetInteraction.insertRefusal(null, HostFixtures.CAROL, BusRole.NODE));
        assertEquals(CabinetInteraction.Refusal.LINKED,
                CabinetInteraction.insertRefusal(null, HostFixtures.CAROL, BusRole.FULL));
        assertEquals("neroarcade.cabinet.msg.linked_insert", CabinetInteraction.Refusal.LINKED.key);
    }

    @Test
    void insertIntoAFreeSoloOrWaitingCabinetIsAllowed() {
        assertNull(CabinetInteraction.insertRefusal(null, HostFixtures.CAROL, BusRole.SOLO));
        assertNull(CabinetInteraction.insertRefusal(null, HostFixtures.CAROL, BusRole.NODE_NO_HOST));
        assertNull(CabinetInteraction.insertRefusal(new Seat(0, HostFixtures.CAROL, 0), HostFixtures.CAROL,
                BusRole.SOLO), "your own seat");
    }

    @Test
    void aStrangerCannotEjectTheHostWhileANodeIsSeatedInTheLobby() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("coop", 2),
                new HostFixtures.CountingWriter(), 2);
        assertTrue(host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE)));
        assertEquals(BusPhase.LOBBY, host.phase());
        // the host cabinet itself is unseated; Carol sneak-uses it
        assertFalse(CabinetInteraction.mayEject(null, host, HostFixtures.CAROL));
        assertTrue(CabinetInteraction.mayEject(null, host, HostFixtures.BOB), "a player seated on the bus may");
        assertNotNull(host.seat(1), "the seat stays");
    }

    @Test
    void anEmptyBusOrOwnSeatMayEject() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(),
                new HostFixtures.CountingWriter());
        assertTrue(CabinetInteraction.mayEject(null, host, HostFixtures.CAROL), "nobody seated");
        assertTrue(CabinetInteraction.mayEject(null, null, HostFixtures.CAROL), "asleep");
        Seat alice = new Seat(0, HostFixtures.ALICE, 0);
        assertTrue(CabinetInteraction.mayEject(alice, null, HostFixtures.ALICE));
        assertFalse(CabinetInteraction.mayEject(alice, null, HostFixtures.CAROL));
    }
}
