package za.co.neroland.neroarcade.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ArcadeSchema;
import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * Cartridge identity on SAVE (review 005a F7): an id is never shared by two cartridges. In place only for the
 * saver's own cartridge in the tray; everything onto a Blank mints a new id and records the origin in the lineage.
 */
class SavePlanTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
    private static final String ORIGINAL = "kkkkkkkkkkkkkkkk";
    private static final String MINTED = "qqqqqqqqqqqqqqqq";
    private static final String UNRELATED = "zzzzzzzzzzzzzzzz";

    @BeforeEach
    void registry() {
        TestModules.installShipped();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    /** A cartridge {@code ORIGINAL} as LOAD reads it: by {@code author} (or authorless), with {@code lineage}. */
    private static BenchDraft loaded(Optional<UUID> author, List<String> lineage) {
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.RULE, List.of(SlotEntry.of(ModuleDefaults.RULE)));
        GameDef def = new GameDef(ArcadeSchema.CURRENT, ORIGINAL, "Neon Dodge", author, Optional.empty(), lineage,
                Optional.of(42L), slots, 5L, 3);
        return BenchDraft.fromGameDef(def);
    }

    private static SavePlan save(BenchDraft draft, UUID saver, Optional<String> tray) throws ValidationError {
        return draft.planSave(saver, tray, () -> MINTED, 100L, Optional.empty());
    }

    @Test
    void ownCartridgeInTheTraySavesInPlace() throws ValidationError {
        SavePlan plan = save(loaded(Optional.of(ALICE), List.of()), ALICE, Optional.of(ORIGINAL));
        assertEquals(SavePlan.Kind.IN_PLACE, plan.kind());
        GameDef def = plan.def().orElseThrow();
        assertEquals(ORIGINAL, def.id());
        assertEquals(4, def.revision(), "revision + 1");
        assertEquals(Optional.of(42L), def.seed(), "the author's fixed seed stays");
        assertTrue(def.lineage().isEmpty(), "lineage kept as it was");
    }

    @Test
    void ownCartridgeOntoABlankIsANewIdInTheSameLineage() throws ValidationError {
        SavePlan plan = save(loaded(Optional.of(ALICE), List.of()), ALICE, Optional.empty());
        assertEquals(SavePlan.Kind.NEW_CARTRIDGE, plan.kind());
        GameDef def = plan.def().orElseThrow();
        assertEquals(MINTED, def.id(), "never a second cartridge with the same id");
        assertEquals(1, def.revision());
        assertEquals(List.of(ORIGINAL), def.lineage());
        assertEquals(ORIGINAL, def.lineageRoot(), "same lineage root, distinct id: a fresh leaderboard (boards are per id)");
        assertEquals(Optional.of(ALICE), def.authorUuid());
        CartridgeValidator.validate(def);
    }

    @Test
    void someoneElsesCartridgeOntoABlankIsTheSaversRemix() throws ValidationError {
        SavePlan plan = save(loaded(Optional.of(ALICE), List.of()), BOB, Optional.empty());
        assertEquals(SavePlan.Kind.NEW_CARTRIDGE, plan.kind());
        GameDef def = plan.def().orElseThrow();
        assertNotEquals(ORIGINAL, def.id(), "never the original's id");
        assertEquals(MINTED, def.id());
        assertEquals(List.of(ORIGINAL), def.lineage());
        assertEquals(Optional.of(BOB), def.authorUuid(), "author = saver");
        assertEquals(1, def.revision());
        assertTrue(def.seed().isEmpty(), "another builder's fixed seed is not carried into a remix");
    }

    @Test
    void anAuthorlessCartridgeIsNeverOwn() throws ValidationError {
        // gallery cartridges, fixtures, anything minted without a player
        SavePlan onBlank = save(loaded(Optional.empty(), List.of()), ALICE, Optional.empty());
        assertEquals(SavePlan.Kind.NEW_CARTRIDGE, onBlank.kind());
        GameDef def = onBlank.def().orElseThrow();
        assertNotEquals(ORIGINAL, def.id());
        assertEquals(List.of(ORIGINAL), def.lineage());
        assertEquals(Optional.of(ALICE), def.authorUuid());

        SavePlan inPlace = save(loaded(Optional.empty(), List.of()), ALICE, Optional.of(ORIGINAL));
        assertEquals(SavePlan.Kind.REFUSED_NOT_YOURS, inPlace.kind(), "nobody adopts an authorless id");
        assertTrue(inPlace.def().isEmpty());
    }

    @Test
    void someoneElsesCartridgeInTheTrayIsRefused() throws ValidationError {
        SavePlan plan = save(loaded(Optional.of(ALICE), List.of()), BOB, Optional.of(ORIGINAL));
        assertEquals(SavePlan.Kind.REFUSED_NOT_YOURS, plan.kind());
        assertEquals("neroarcade.bench.status.not_yours", plan.kind().statusKey());
        assertTrue(plan.refused());
    }

    @Test
    void aDifferentGameInTheTrayIsRefused() throws ValidationError {
        SavePlan own = save(loaded(Optional.of(ALICE), List.of()), ALICE, Optional.of(UNRELATED));
        assertEquals(SavePlan.Kind.REFUSED_OTHER_CARTRIDGE, own.kind());
        assertEquals("neroarcade.bench.status.other_cartridge", own.kind().statusKey());

        BenchDraft fresh = new BenchDraft();
        fresh.setTitle("Brand new");
        assertEquals(SavePlan.Kind.REFUSED_OTHER_CARTRIDGE, save(fresh, ALICE, Optional.of(UNRELATED)).kind(),
                "a never-saved draft cannot overwrite a written cartridge");
        SavePlan first = save(fresh, ALICE, Optional.empty());
        assertEquals(SavePlan.Kind.NEW_CARTRIDGE, first.kind());
        assertTrue(first.def().orElseThrow().lineage().isEmpty(), "a new game has no lineage");
    }

    @Test
    void lineageIsCappedAtEightAndKeepsItsRoot() throws ValidationError {
        List<String> full = new ArrayList<>();
        for (int i = 0; i < CartridgeIds.MAX_LINEAGE; i++) {
            full.add("bbbbbbbbbbbbbbb" + (char) ('a' + i));
        }
        GameDef def = save(loaded(Optional.of(ALICE), full), BOB, Optional.empty()).def().orElseThrow();
        assertEquals(CartridgeIds.MAX_LINEAGE, def.lineage().size());
        assertEquals(full.get(0), def.lineageRoot(), "the root survives the cap");
        assertEquals(ORIGINAL, def.lineage().get(def.lineage().size() - 1), "the parent is last");
        assertEquals(full.subList(full.size() - (CartridgeIds.MAX_LINEAGE - 2), full.size()),
                def.lineage().subList(1, CartridgeIds.MAX_LINEAGE - 1), "then the most recent six");
        CartridgeValidator.validate(def);
    }

    @Test
    void noTitleIsStillRefusedFirst() {
        BenchDraft draft = new BenchDraft();
        assertThrows(ValidationError.class, () -> save(draft, ALICE, Optional.empty()));
    }
}
