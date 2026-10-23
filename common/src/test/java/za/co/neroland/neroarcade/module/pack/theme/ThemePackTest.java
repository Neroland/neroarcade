package za.co.neroland.neroarcade.module.pack.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.client.screen.ThemePalettes;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ThemeModule;
import za.co.neroland.neroarcade.module.hook.ThemeSpec;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

class ThemePackTest {

    private static final Path ASSETS = Path.of("common/src/main/resources/assets/neroarcade");

    @BeforeEach
    void registry() {
        ModuleRegistry.resetForTests();
        ArcadeModules.registerDefaults();
        ThemePack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static List<ThemeModule> themes() {
        return ModuleRegistry.byType(ModuleType.THEME).stream().map(m -> (ThemeModule) m).toList();
    }

    @Test
    void registersFourThemesSortedById() {
        List<String> ids = themes().stream().map(m -> m.id().getPath()).toList();
        assertEquals(List.of("theme/cindara_dusk", "theme/hangar", "theme/neon_void", "theme/terminal_green"), ids);
        assertSame(HangarTheme.INSTANCE, ModuleRegistry.get(HangarTheme.INSTANCE.id()).orElseThrow());
        assertSame(CindaraDuskTheme.INSTANCE, ModuleRegistry.get(CindaraDuskTheme.INSTANCE.id()).orElseThrow());
        assertSame(TerminalGreenTheme.INSTANCE, ModuleRegistry.get(TerminalGreenTheme.INSTANCE.id()).orElseThrow());
    }

    @Test
    void allFourResolveToDistinctClientThemes() {
        Set<ThemePalettes.Theme> seen = EnumSet.noneOf(ThemePalettes.Theme.class);
        for (ThemeModule theme : themes()) {
            ThemeSpec spec = theme.spec(PropertyValues.defaults(theme));
            ThemePalettes.Theme bySprites = ThemePalettes.themeOf(spec.spriteSetId().getPath());
            assertEquals(bySprites, ThemePalettes.themeOf(spec.paletteId().getPath()),
                    theme.id() + ": palette and sprites name the same theme");
            assertEquals(theme.id().getPath().substring("theme/".length()), bySprites.folder(),
                    theme.id() + " must not fall back to the default sheets");
            assertTrue(seen.add(bySprites), theme.id() + " shares a client theme");
            Path folder = TestFiles.repoRoot().resolve(ASSETS).resolve("textures/screen").resolve(bySprites.folder());
            assertTrue(Files.isDirectory(folder), "no sheets at " + folder);
        }
        assertEquals(EnumSet.allOf(ThemePalettes.Theme.class), seen);
    }

    @Test
    void everySoundIdExistsInSoundsJson() {
        JsonObject sounds = JsonParser.parseString(TestFiles.read(TestFiles.repoRoot().resolve(ASSETS)
                .resolve("sounds.json"))).getAsJsonObject();
        for (ArcadeModule module : ModuleRegistry.byType(ModuleType.THEME)) {
            ThemeModule theme = (ThemeModule) module;
            ThemeSpec spec = theme.spec(PropertyValues.defaults(theme));
            for (Identifier sound : List.of(spec.soundStart(), spec.soundPickup(), spec.soundEnd())) {
                assertEquals("neroarcade", sound.getNamespace(), theme.id() + " " + sound);
                assertTrue(sounds.has(sound.getPath()), theme.id() + ": " + sound + " is not in sounds.json");
            }
            String name = theme.id().getPath().substring("theme/".length());
            assertTrue(sounds.has("theme." + name + ".loop"), theme.id() + ": the client's ambient loop exists too");
        }
    }
}
