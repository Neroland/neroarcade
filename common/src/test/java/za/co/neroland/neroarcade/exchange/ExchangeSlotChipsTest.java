package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.client.bench.BenchModel;
import za.co.neroland.neroarcade.client.exchange.ExchangeSlotTips;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.network.ExchangePagePayload;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * Stage 5 review F1: the Exchange pane names every one of a listing's ten slots — the row carries each slot's chip
 * ids ({@code slotChips}), they survive the codec, they are exactly what the bench rack shows for the cartridge, and
 * every cell has a glyph and a tooltip whose keys are in the lang file.
 */
class ExchangeSlotChipsTest {

    @BeforeEach
    void modules() {
        TestModules.installShipped();
    }

    @AfterEach
    void reset() {
        TestModules.reset();
    }

    private static ExchangePagePayload page(List<ExchangePagePayload.Row> rows) {
        return new ExchangePagePayload(ExchangePagePayload.TAB_LISTINGS, 0, 1, rows.size(), rows,
                ExchangePagePayload.FLAG_ENABLED, "", List.of(), ExchangePagePayload.STATUS_NEUTRAL, 0, 8);
    }

    private static ExchangePagePayload roundTrip(ExchangePagePayload page) {
        ByteBuf buf = Unpooled.buffer();
        ExchangePagePayload.STREAM_CODEC.encode(buf, page);
        return ExchangePagePayload.STREAM_CODEC.decode(buf);
    }

    @Test
    void slotChipsSurviveTheCodec() {
        List<String> chips = new ArrayList<>();
        for (int i = 0; i < ExchangePagePayload.SLOTS; i++) {
            chips.add("");
        }
        chips.set(ModuleType.HAZARD.ordinal(), "neroarcade:hazard/falling_block,neroarcade:hazard/chaser");
        chips.set(ModuleType.ARENA.ordinal(), "neroarcade:arena/grid");
        ExchangePagePayload.Row row = new ExchangePagePayload.Row(7, "Neon Dodge", "", List.of("grid"), 3, 1,
                new int[ExchangePagePayload.SLOTS], chips, 0, 0);
        ExchangePagePayload.Row back = roundTrip(page(List.of(row))).rows().get(0);
        assertEquals(chips, back.slotChips());
        assertEquals(List.of("neroarcade:hazard/falling_block", "neroarcade:hazard/chaser"),
                back.chipsIn(ModuleType.HAZARD.ordinal()));
        assertTrue(back.chipsIn(ModuleType.PICKUP.ordinal()).isEmpty(), "an empty slot has no ids");
    }

    @Test
    void aShortOrMissingListIsPaddedToTenEmptySlots() {
        ExchangePagePayload.Row row = new ExchangePagePayload.Row(1, "T", "", List.of(), 0, 0, null, null, 0, 0);
        assertEquals(ExchangePagePayload.SLOTS, row.slotChips().size());
        assertTrue(row.slotChips().stream().allMatch(String::isEmpty));
        assertEquals(ExchangePagePayload.SLOTS, roundTrip(page(List.of(row))).rows().get(0).slotChips().size());
    }

    @Test
    void everyExampleListsTheIdsTheBenchRackShows() throws Exception {
        TestModules.install(); // the example cartridges decode against the same registry ExampleCartridgesTest uses
        List<Path> files = TestFiles.cartridges();
        assertFalse(files.isEmpty());
        for (Path file : files) {
            GameDef def = CartridgeValidator.decodeJson(TestFiles.read(file), Budgets.MAX_IMPORT_JSON_BYTES);
            ExchangeTable table = new ExchangeTable();
            ExchangeFixtures.publish(table, def, 1, 200);
            ExchangePagePayload.Row row = roundTrip(ExchangePages.listings(table.browse(), 0, 8, ExchangeFixtures.BOB,
                    (u, id) -> false, true, false, ExchangePages.Status.NONE, 0, 8)).rows().get(0);
            for (ModuleType type : ModuleType.values()) {
                List<String> rack = def.slot(type).stream().map(SlotEntry::id).map(Object::toString).toList();
                assertEquals(rack, row.chipsIn(type.ordinal()), file.getFileName() + " slot " + type);
                assertEquals(!rack.isEmpty(), ExchangeSlotTips.filled(row, type.ordinal()),
                        file.getFileName() + " slot " + type + " filled");
            }
        }
    }

    @Test
    void everySlotCellHasAGlyphAndATooltipInTheLangFile() {
        Map<String, String> lang = TestFiles.lang();
        assertEquals("Empty — the game uses the default", lang.get(ExchangeSlotTips.SLOT_DEFAULT_KEY));
        assertTrue(lang.containsKey(ExchangeSlotTips.UNKNOWN_CHIP_KEY));
        ExchangePagePayload.Row empty = new ExchangePagePayload.Row(1, "T", "", List.of(), 0, 0, null, null, 0, 0);
        for (ModuleType type : ModuleType.values()) {
            int lit = 0;
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    lit += BenchModel.glyph(type, x, y) ? 1 : 0;
                }
            }
            assertTrue(lit > 0, type + " has a glyph");
            assertTrue(lang.containsKey(ExchangeSlotTips.slotKey(type.ordinal())), type + " slot word");
            assertEquals(List.of(ExchangeSlotTips.SLOT_DEFAULT_KEY), ExchangeSlotTips.chipKeys(empty, type.ordinal()));
        }
        for (var module : za.co.neroland.neroarcade.module.ModuleRegistry.all()) {
            String id = module.id().toString();
            String key = ExchangeSlotTips.chipLangKey(id);
            assertEquals(module.langKey(), key);
            assertTrue(lang.containsKey(key), id + " has a name");
        }
        assertEquals(ExchangeSlotTips.UNKNOWN_CHIP_KEY, ExchangeSlotTips.chipLangKey("someone:not/here"));
        assertEquals(ExchangeSlotTips.UNKNOWN_CHIP_KEY, ExchangeSlotTips.chipLangKey("Not An Id"));
    }
}
