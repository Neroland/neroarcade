package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.multiplayer.VersusMultiplayer;
import za.co.neroland.neroarcade.module.pack.pawn.RunnerPawn;
import za.co.neroland.neroarcade.module.pack.arena.Strip9Arena;

/**
 * 003 §8.1: genre words come from each chip's own {@code genreTag()} (never another mod's ids), in slot order, at most
 * six, each a shipped lang key; only ARENA, PAWN, RULE, MULTIPLAYER and THEME chips carry one.
 */
class GenreTagsTest {

    @BeforeEach
    void modules() {
        TestModules.installShipped();
    }

    @AfterEach
    void reset() {
        TestModules.reset();
    }

    @Test
    void derivedFromTheChipsInSlotOrder() {
        assertEquals(List.of("strip", "run", "survive", "versus", "neon"), GenreTags.of(TestModules.resolvedOf(
                Strip9Arena.INSTANCE, RunnerPawn.INSTANCE, VersusMultiplayer.INSTANCE)));
    }

    @Test
    void everyShippedWordIsWellFormedAndTranslated() {
        Map<String, String> lang = TestFiles.lang();
        for (ArcadeModule module : ModuleRegistry.all()) {
            String word = module.genreTag();
            boolean tagged = switch (module.type()) {
                case ARENA, PAWN, RULE, MULTIPLAYER, THEME -> true;
                default -> false;
            };
            if (tagged) {
                assertNotNull(word, module.id() + " carries a genre word");
                assertTrue(GenreTags.WORD.matcher(word).matches(), word);
                assertTrue(lang.containsKey(GenreTags.langKey(word)), "lang for " + word);
            } else {
                assertNull(word, module.id() + " carries no genre word");
            }
        }
        assertTrue(ModuleType.values().length >= GenreTags.MAX_TAGS);
    }
}
