package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 002 R6 / 003 §2.5 on a bus node seat: the 31st packet in a second is dropped, the next second's first accepted, the seat kept. */
class InputRateTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    @Test
    void overBudgetDropsNeverUnseats() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame("versus", 2),
                new HostFixtures.CountingWriter(), 2);
        Seat bob = new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE);
        host.bind(1, bob);
        int budget = Budgets.maxInputPacketsPerSecond();
        assertEquals(30, budget);
        env.time = 100;
        for (int i = 0; i < budget; i++) {
            assertEquals(InputGate.Verdict.ACCEPTED,
                    host.offer(1, HostFixtures.BOB, host.matchId(), SeatInput.of(i % 2 == 0 ? SeatInput.UP : 0, 0, 0)));
        }
        assertEquals(InputGate.Verdict.RATE_LIMITED,
                host.offer(1, HostFixtures.BOB, host.matchId(), SeatInput.of(SeatInput.DOWN, 0, 0)));
        assertEquals(1, bob.droppedInputs());
        env.time += InputGate.WINDOW_TICKS;
        assertEquals(InputGate.Verdict.ACCEPTED,
                host.offer(1, HostFixtures.BOB, host.matchId(), SeatInput.of(SeatInput.DOWN, 0, 0)));
        assertSame(bob, host.seat(1), "still seated");
    }
}
