package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.world.SimpleContainer;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * Review 003b F5 (per-author listing cap, admin purge), F6 (the kiosk tray is per viewer, the kiosk holds no items)
 * and F11 (an asynchronous publish completes only while its kiosk is open; the gate goes to the publisher).
 */
class ExchangeFloodAndTrayTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        za.co.neroland.neroarcade.TestBootstrap.ensure();
    }

    private static final int WORLD_CAP = 200;
    private static final int AUTHOR_CAP = 16;

    private static ExchangeTable.PublishResult publish(ExchangeTable table, GameDef def, int authorCap) {
        return table.publish(def, ExchangeRules.listingAuthor(def.authorUuid()), Optional.empty(),
                ExchangeFixtures.TAGS, 1L, WORLD_CAP, authorCap);
    }

    @Test
    void oneAuthorCannotFillTheExchange() {
        ExchangeTable table = new ExchangeTable();
        for (int i = 0; i < AUTHOR_CAP; i++) {
            assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                    publish(table, ExchangeFixtures.game(i, ExchangeFixtures.ALICE, null), AUTHOR_CAP).status());
        }
        ExchangeTable.PublishResult refused = publish(table,
                ExchangeFixtures.game(AUTHOR_CAP, ExchangeFixtures.ALICE, null), AUTHOR_CAP);
        assertEquals(ExchangeTable.PublishStatus.AUTHOR_FULL, refused.status());
        assertEquals(AUTHOR_CAP, table.listingCount(ExchangeFixtures.ALICE));
        // a re-publish of an existing id is a replace, never refused at the cap
        assertEquals(ExchangeTable.PublishStatus.REPLACED,
                publish(table, ExchangeFixtures.game(3, ExchangeFixtures.ALICE, null), AUTHOR_CAP).status());
        // someone else still can
        assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                publish(table, ExchangeFixtures.game(500, ExchangeFixtures.BOB, null), AUTHOR_CAP).status());
        // an op (authorCap 0) is exempt
        assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                publish(table, ExchangeFixtures.game(AUTHOR_CAP, ExchangeFixtures.ALICE, null), 0).status());
    }

    @Test
    void adminUnpublishRemovesEveryListingOfOneAuthorOnly() {
        ExchangeTable table = new ExchangeTable();
        for (int i = 0; i < 5; i++) {
            publish(table, ExchangeFixtures.game(i, ExchangeFixtures.ALICE, null), 0);
        }
        publish(table, ExchangeFixtures.game(100, ExchangeFixtures.BOB, null), 0);
        int before = table.version();
        assertEquals(5, table.unpublishBy(ExchangeFixtures.ALICE));
        assertEquals(1, table.size());
        assertTrue(table.listingByCartridgeId(ExchangeFixtures.id(100)).isPresent(), "Bob's listing stays");
        assertTrue(table.version() != before, "viewers refresh");
        assertEquals(0, table.unpublishBy(ExchangeFixtures.ALICE), "idempotent");
        assertEquals(0, table.unpublishBy(ExchangeFixtures.CAROL), "an unknown player is fine");
    }

    @Test
    void everyMenuGetsItsOwnTrayAndTheKioskHoldsNone() throws Exception {
        SimpleContainer a = ExchangeMenu.newTray();
        SimpleContainer b = ExchangeMenu.newTray();
        assertNotSame(a, b, "two players at one kiosk never share a tray");
        assertEquals(1, a.getContainerSize());
        for (Method m : ArcadeExchangeBlockEntity.class.getDeclaredMethods()) {
            assertFalse(m.getName().equals("tray"), "the kiosk exposes no shared tray");
        }
        for (Field f : ArcadeExchangeBlockEntity.class.getDeclaredFields()) {
            assertFalse(f.getName().equals("tray"), "the kiosk holds no shared tray");
        }
    }

    @Test
    void anAsyncPublishCompletesOnlyWhileTheKioskIsOpen() {
        assertTrue(Exchange.completes(true, true));
        assertFalse(Exchange.completes(true, false), "the kiosk was closed: nothing is listed");
        assertFalse(Exchange.completes(false, true), "the publisher logged out");
    }

    @Test
    void theFirstPublishedGateGoesToThePublisher() {
        assertEquals(ExchangeFixtures.BOB, Exchange.gateFor(ExchangeFixtures.BOB),
                "an op publishing Alice's game earns it themselves; Alice is not handed it");
    }
}
