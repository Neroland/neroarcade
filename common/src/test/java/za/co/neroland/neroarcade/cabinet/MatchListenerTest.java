package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * 003 §6.1 / §8.1: the match listener hears each finished match once, after the board write, and never an abandoned
 * one; the Exchange counts a play only on the first match of an insertion (a host lives for one insertion). A
 * downloaded copy keeps the id and lineage, so its plays land on the listing.
 */
class MatchListenerTest {

    @Test
    void downloadKeepsIdLineageAndPlaysCountOnlyOnTheFirstMatch() throws Exception {
        FakeHostEnv env = new FakeHostEnv();
        HostFixtures.CountingWriter writer = new HostFixtures.CountingWriter();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), writer);
        GameDef def = host.game().def();

        // the listed game, and a "downloaded" copy of it (same encoding a download writes)
        ExchangeTable exchange = new ExchangeTable();
        exchange.publish(def, TestModules.AUTHOR, Optional.empty(), List.of(), 1L, 200);
        GameDef copy = StoredCartridge.of(def).decode();
        assertEquals(def.id(), copy.id());
        assertEquals(def.lineageRoot(), copy.lineageRoot());

        List<MatchListener.MatchEnd> ends = new ArrayList<>();
        host.listener(end -> {
            ends.add(end);
            if (end.firstPlay()) {
                exchange.countPlay(end.result().cartridgeId());
            }
        });
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(1, ends.size());
        assertTrue(ends.get(0).firstPlay());
        assertEquals(1, writer.calls, "the board write still happens first");
        host.pressStart(0);
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(2, ends.size());
        assertFalse(ends.get(1).firstPlay(), "a rematch on the same insertion is not a new play");
        assertEquals(1, exchange.listingByCartridgeId(copy.id()).orElseThrow().plays());
    }

    @Test
    void abandonedMatchesReachNoListener() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        List<MatchListener.MatchEnd> ends = new ArrayList<>();
        host.listener(ends::add);
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 80);
        host.unbind(0, DropReason.ESC);
        HostFixtures.tick(host, env, MatchHost.RESULTS_TICKS + 10);
        assertTrue(ends.isEmpty());
    }

    @Test
    void aThrowingListenerNeverBreaksTheMatch() {
        FakeHostEnv env = new FakeHostEnv();
        MatchHost host = MatchHost.solo(env, HostFixtures.POS, HostFixtures.tenSecondGame(), BoardWriter.NONE);
        host.listener(end -> {
            throw new IllegalStateException("boom");
        });
        host.bind(0, new Seat(0, HostFixtures.ALICE, 0));
        HostFixtures.tick(host, env, 60 + HostFixtures.GAME_TICKS);
        assertEquals(BusPhase.RESULTS, host.phase());
    }
}
