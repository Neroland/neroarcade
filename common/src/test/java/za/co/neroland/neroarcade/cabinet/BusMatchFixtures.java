package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A two-seat coop bus match already in PLAYING (Alice at the host, Bob at a node). */
final class BusMatchFixtures {

    final FakeHostEnv env = new FakeHostEnv();
    final HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
    final MatchHost host;

    BusMatchFixtures(String chip) {
        host = new MatchHost(env, HostFixtures.POS, HostFixtures.tenSecondGame(chip, 2), writer, 2);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0, HostFixtures.POS));
        host.bind(1, new Seat(1, HostFixtures.BOB, 0, HostFixtures.NODE));
        host.pressStart(0);
        host.pressStart(1);
        HostFixtures.tick(host, env, 61);
        assertEquals(BusPhase.PLAYING, host.phase());
    }
}
