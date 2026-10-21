package za.co.neroland.neroarcade.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;

/** The bench draft: chips → GameDef with defaults, edits, SAVE identity rules, LOAD round-trip, unreadable. */
class BenchDraftTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
    private static final String MINTED = "qqqqqqqqqqqqqqqq";

    @BeforeEach
    void registry() {
        TestModules.installShipped();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static Identifier[] rack(Object... indexAndId) {
        Identifier[] chips = new Identifier[BenchSlots.CHIP_SLOTS];
        for (int i = 0; i < indexAndId.length; i += 2) {
            chips[(Integer) indexAndId[i]] = (Identifier) indexAndId[i + 1];
        }
        return chips;
    }

    @Test
    void layoutIsTenSlotsInFourteenPositions() {
        assertEquals(0, BenchSlots.index(ModuleType.ARENA, 0));
        assertEquals(3, BenchSlots.index(ModuleType.HAZARD, 0));
        assertEquals(5, BenchSlots.index(ModuleType.HAZARD, 2));
        assertEquals(8, BenchSlots.index(ModuleType.PICKUP, 2));
        assertEquals(13, BenchSlots.index(ModuleType.THEME, 0));
        assertEquals(-1, BenchSlots.index(ModuleType.PAWN, 1));
        assertEquals(ModuleType.PICKUP, BenchSlots.typeAt(7));
        assertEquals(1, BenchSlots.stackAt(7));
    }

    @Test
    void emptyRackIsAllDefaultsAndValidates() throws ValidationError {
        BenchDraft draft = new BenchDraft();
        GameDef preview = draft.preview();
        assertTrue(preview.slots().isEmpty(), "empty slots are absent, never written as defaults");
        assertEquals(BenchDraft.PLACEHOLDER_TITLE, preview.title());
        assertTrue(preview.authorUuid().isEmpty(), "the preview never carries an author");
        ResolvedGame game = CartridgeValidator.validate(preview);
        assertEquals(ModuleDefaults.PAWN, game.pawn().id());
    }

    @Test
    void chipsAndPropertiesBecomeSlotsAndAChipSwapClearsValues() throws ValidationError {
        BenchDraft draft = new BenchDraft();
        int rule = BenchSlots.index(ModuleType.RULE, 0);
        assertTrue(draft.syncChips(rack(rule, ModuleDefaults.RULE)));
        assertTrue(draft.setProp(rule, "seconds", 999, false), "clamped, still a change");
        assertEquals(new PropertyValue.IntValue(600), draft.propsAt(rule).get("seconds"), "INT clamps to the spec max");
        assertFalse(draft.setProp(rule, "nonexistent", 5, false));
        assertTrue(draft.setProp(rule, "seconds", 60, false));
        assertTrue(draft.propsAt(rule).isEmpty(), "the default is not stored");
        draft.setProp(rule, "lives", 3, false);

        GameDef preview = draft.preview();
        assertEquals(List.of(new SlotEntry(ModuleDefaults.RULE, Map.of("lives", new PropertyValue.IntValue(3)))),
                preview.slot(ModuleType.RULE));
        CartridgeValidator.validate(preview);

        draft.syncChips(rack());
        assertTrue(draft.propsAt(rule).isEmpty(), "removing the chip drops its values");
        assertTrue(draft.preview().slots().isEmpty());
    }

    @Test
    void enumAndBoolEditsClampToTheSpec() {
        BenchDraft draft = new BenchDraft();
        int arena = BenchSlots.index(ModuleType.ARENA, 0);
        int control = BenchSlots.index(ModuleType.CONTROL, 0);
        draft.syncChips(rack(arena, ModuleDefaults.ARENA, control, ModuleDefaults.CONTROL));
        draft.setProp(arena, "obstacles", 99, false);
        assertEquals(new PropertyValue.EnumValue("maze_lite"), draft.propsAt(arena).get("obstacles"));
        draft.setProp(control, "diagonals", 0, true);
        assertEquals(new PropertyValue.BoolValue(true), draft.propsAt(control).get("diagonals"));
    }

    @Test
    void saveNewThenResaveThenRemix() throws ValidationError {
        BenchDraft draft = new BenchDraft();
        assertThrows(ValidationError.class, () -> draft.planSave(ALICE, () -> MINTED, 10L, Optional.empty()),
                "no title yet");
        draft.setTitle("Neon Dodge");
        GameDef first = draft.planSave(ALICE, () -> MINTED, 10L, Optional.empty());
        assertEquals(MINTED, first.id());
        assertEquals(1, first.revision());
        assertEquals(Optional.of(ALICE), first.authorUuid());
        assertTrue(first.authorCredit().isEmpty(), "credit is opt-in; none until Stage 6");
        assertTrue(first.lineage().isEmpty());
        CartridgeValidator.validate(first);
        draft.adopt(first);

        GameDef second = draft.planSave(ALICE, () -> "should not mint", 20L, Optional.empty());
        assertEquals(MINTED, second.id(), "the same author keeps the id");
        assertEquals(2, second.revision());

        BenchDraft other = BenchDraft.fromGameDef(second);
        GameDef remix = other.planSave(BOB, () -> "rrrrrrrrrrrrrrrr", 30L, Optional.empty());
        assertNotEquals(MINTED, remix.id(), "someone else's save is a remix");
        assertEquals(List.of(MINTED), remix.lineage());
        assertEquals(MINTED, remix.lineageRoot());
        assertEquals(1, remix.revision());
        assertEquals(Optional.of(BOB), remix.authorUuid());
        assertTrue(CartridgeIds.isValid(remix.id()));
    }

    @Test
    void loadRoundTripsThroughTheCartridgeComponent() throws ValidationError {
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.RULE, List.of(new SlotEntry(ModuleDefaults.RULE, Map.of("seconds", new PropertyValue.IntValue(30)))));
        slots.put(ModuleType.THEME, List.of(SlotEntry.of(ModuleDefaults.THEME)));
        GameDef def = TestModules.def(slots);
        BenchDraft.Load load = BenchDraft.load(StoredCartridge.of(def));
        assertTrue(load.error().isEmpty());
        BenchDraft draft = load.draft().orElseThrow();
        assertEquals(def.slots(), draft.preview().slots());
        assertEquals(def.title(), draft.title());
        assertEquals(ModuleDefaults.RULE, draft.chipAt(BenchSlots.index(ModuleType.RULE, 0)));

        Identifier[] same = new Identifier[BenchSlots.CHIP_SLOTS];
        for (int i = 0; i < same.length; i++) {
            same[i] = draft.chipAt(i);
        }
        assertFalse(draft.syncChips(same), "materialised chips match the draft: values survive");
        assertEquals(def.slots(), draft.preview().slots());
    }

    @Test
    void unknownModuleCartridgeIsUnreadableHereNotACrash() throws ValidationError {
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.PAWN, List.of(SlotEntry.of(TestModules.id("pawn/not_on_this_server"))));
        StoredCartridge stored = StoredCartridge.of(TestModules.def(slots));
        BenchDraft.Load load = BenchDraft.load(stored);
        assertTrue(load.draft().isEmpty());
        assertEquals(ValidationError.Code.UNKNOWN_MODULE, load.error().orElseThrow().code);
        assertEquals("Test Game", stored.rawTitle(), "the title still reads for the tooltip");
    }

    @Test
    void draftPersistsThroughItsCodec() {
        BenchDraft draft = new BenchDraft();
        int rule = BenchSlots.index(ModuleType.RULE, 0);
        draft.syncChips(rack(rule, ModuleDefaults.RULE));
        draft.setProp(rule, "lives", 4, false);
        draft.setTitle("Persist me");
        Tag tag = BenchDraft.CODEC.encodeStart(NbtOps.INSTANCE, draft).getOrThrow();
        BenchDraft back = BenchDraft.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals("Persist me", back.title());
        assertEquals(draft.preview().slots(), back.preview().slots());
    }

    @Test
    void titleRules() {
        assertEquals(Optional.of("Neon Dodge"), TitleRules.clean("  Neon\u0007 Dodge \n"));
        assertTrue(TitleRules.clean("   ").isEmpty());
        assertTrue(TitleRules.clean("x".repeat(33)).isEmpty(), "33 characters is refused, never truncated");
        assertEquals(32, TitleRules.clean("y".repeat(32)).orElseThrow().length());
    }
}
