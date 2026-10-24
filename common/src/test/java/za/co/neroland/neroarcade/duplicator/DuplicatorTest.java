package za.co.neroland.neroarcade.duplicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.item.StoredCartridge;

/**
 * 003 §7: a copy is identical — id, lineage, revision, author UUID and credit unchanged, through the item encoding —
 * and COPY needs an original, a Blank Cartridge, empty outputs and power, each refusal one sentence.
 */
class DuplicatorTest {

    private static final UUID AUTHOR = UUID.fromString("11111111-2222-4333-8444-555555555555");

    /** Decoding a cartridge resolves its empty slots against the registry: never rely on another test's install. */
    @BeforeEach
    void registry() {
        za.co.neroland.neroarcade.module.TestModules.installShipped();
    }

    @AfterEach
    void tearDown() {
        za.co.neroland.neroarcade.module.TestModules.reset();
    }

    private static GameDef original() {
        return new GameDef(1, "abcdefghijklmnop", "Neon Dodge", Optional.of(AUTHOR), Optional.of("StarPilot"),
                List.of("qrstuvwxyz234567"), Optional.of(42L), Map.of(), 1_760_000_000L, 3);
    }

    @Test
    void duplicatePreservesIdLineageRevisionAndCredit() throws Exception {
        GameDef def = original();
        GameDef copy = StoredCartridge.of(DuplicatorRules.copyOf(def)).decode();
        assertEquals(def.id(), copy.id());
        assertEquals(def.lineage(), copy.lineage());
        assertEquals(def.lineageRoot(), copy.lineageRoot());
        assertEquals(def.id(), copy.id(), "copies keep the id, so they pool onto one leaderboard (boards are per id)");
        assertEquals(def.revision(), copy.revision());
        assertEquals(def.authorUuid(), copy.authorUuid());
        assertEquals(def.authorCredit(), copy.authorCredit());
        assertEquals(def, copy);
    }

    @Test
    void duplicatorNeedsABlank() {
        Optional<GameDef> game = Optional.of(original());
        assertEquals(Optional.of(DuplicatorRules.Deny.NEED_BLANK),
                DuplicatorRules.check(game, true, false, true, true, false));
        assertEquals(Optional.of(DuplicatorRules.Deny.NEED_ORIGINAL),
                DuplicatorRules.check(Optional.empty(), false, true, true, true, false));
        assertEquals(Optional.of(DuplicatorRules.Deny.UNREADABLE),
                DuplicatorRules.check(Optional.empty(), true, true, true, true, false));
        assertEquals(Optional.of(DuplicatorRules.Deny.OUTPUT_FULL),
                DuplicatorRules.check(game, true, true, false, true, false));
        assertEquals(Optional.of(DuplicatorRules.Deny.NO_POWER),
                DuplicatorRules.check(game, true, true, true, false, false));
        assertEquals(Optional.of(DuplicatorRules.Deny.BUSY), DuplicatorRules.check(game, true, true, true, true, true));
        assertTrue(DuplicatorRules.check(game, true, true, true, true, false).isEmpty());
        assertEquals(60, DuplicatorRules.COPY_TICKS);
        for (DuplicatorRules.Deny deny : DuplicatorRules.Deny.values()) {
            assertTrue(za.co.neroland.neroarcade.TestFiles.lang().containsKey(deny.langKey()), deny.langKey());
        }
    }
}
