package za.co.neroland.neroarcade.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroarcade.command.GalleryCommands;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.game.GameDef;

/**
 * Final review L1: the gallery's 16 showcase listings must not count towards the {@code cartridge_published} channel,
 * so the first real publish after {@code /neroarcade gallery} crosses rung 1, not rung 10.
 */
class GalleryListingsDoNotCountTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final List<String> TAGS = List.of("grid", "move", "survive", "solo", "neon");

    private final List<ThresholdEvents.ThresholdCrossing> fired = new ArrayList<>();

    @BeforeEach
    void sink() {
        ArcadeMilestones.setSink(new ArcadeMilestones.Sink() {
            @Override
            public void fire(ThresholdEvents.ThresholdCrossing crossing) {
                fired.add(crossing);
            }

            @Override
            public boolean openGate(MinecraftServer server, UUID player, Identifier gate) {
                return false;
            }
        });
    }

    @AfterEach
    void restore() {
        ArcadeMilestones.setSink(null);
    }

    /** A 16-char base32 id from a number. */
    private static String id(int n) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder out = new StringBuilder();
        long v = n;
        for (int i = 0; i < 16; i++) {
            out.append(alphabet.charAt((int) (v & 31)));
            v >>>= 5;
        }
        return out.toString();
    }

    private static GameDef game(int n, UUID author) {
        return new GameDef(1, id(n), "Game " + n, Optional.ofNullable(author), Optional.empty(), List.of(),
                Optional.empty(), Map.of(), 100L, 1);
    }

    @Test
    void theFirstRealPublishAfterTheGalleryCountsAsOne() {
        assertEquals(GalleryCommands.GALLERY_AUTHOR, ExchangeStore.SHOWCASE_AUTHOR, "one showcase author");
        ExchangeTable table = new ExchangeTable();
        for (int n = 0; n < 16; n++) {
            table.publish(game(n, ExchangeStore.SHOWCASE_AUTHOR), ExchangeStore.SHOWCASE_AUTHOR,
                    Optional.of("Gallery"), TAGS, 100L, 200, 0, true);
        }
        assertEquals(ExchangeTable.PublishStatus.PUBLISHED,
                table.publish(game(100, ALICE), ALICE, Optional.empty(), TAGS, 200L, 200).status());
        ExchangeStore store = ExchangeStore.of(table);
        assertEquals(17, store.size(), "the showcase is still listed");
        assertEquals(1, store.publishedCount(), "but only the real listing counts");
        assertEquals(0, table.sizeExcluding(ALICE) - 16, "sizeExcluding drops exactly one author's listings");

        assertTrue(ArcadeMilestones.cartridgePublished(store.publishedCount()));
        assertEquals(1, fired.size());
        assertEquals(1L, fired.get(0).value(), "grand_opening (rung 1) only");
        assertEquals(1L, fired.get(0).threshold(), "busy_arcade (rung 10) does not complete");
    }

    @Test
    void theExchangeCountsPublishedListingsNotTheStoreSize() throws Exception {
        Path source = za.co.neroland.neroarcade.TestFiles.repoRoot()
                .resolve("common/src/main/java/za/co/neroland/neroarcade/exchange/Exchange.java");
        String text = Files.readString(source);
        assertTrue(text.contains("cartridgePublished(store.publishedCount())"), "Exchange uses publishedCount()");
        assertTrue(!text.contains("cartridgePublished(store.size())"), "never the raw store size");
    }
}
