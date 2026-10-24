package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;

class ShippedModulesConventionTest {

    @BeforeEach
    void shipped() {
        TestModules.installShipped();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void idsPropertiesAndLangFollowTheConvention() {
        Map<String, String> lang = TestFiles.lang();
        assertFalse(ModuleRegistry.all().isEmpty());
        for (ArcadeModule module : ModuleRegistry.all()) {
            String id = module.id().toString();
            assertEquals("neroarcade", module.id().getNamespace(), id);
            String prefix = module.type().key() + "/";
            assertTrue(module.id().getPath().startsWith(prefix), id);
            assertTrue(module.id().getPath().substring(prefix.length()).matches("[a-z][a-z0-9_]{0,31}"), id);
            assertTrue(module.properties().size() <= 3, id + " declares more than 3 properties");
            assertTrue(lang.containsKey(module.langKey()), module.langKey());
            String desc = lang.get(module.descLangKey());
            assertTrue(desc != null, module.descLangKey());
            assertFalse(desc.contains("\n"), module.descLangKey());
            assertTrue(desc.length() <= 160, module.descLangKey());
            for (PropertySpec spec : module.properties()) {
                assertTrue(lang.containsKey(spec.langKey(module)), spec.langKey(module));
                assertTrue(spec.name().matches("[a-z][a-z0-9_]{0,15}"), spec.name());
                for (String constant : spec.constants()) {
                    assertTrue(lang.containsKey(spec.constantLangKey(module, constant)), spec.constantLangKey(module, constant));
                }
            }
        }
    }

    @Test
    void reviewedChipsShowNoDeadSettingsAndNoKeyLetters() {
        assertTrue(za.co.neroland.neroarcade.module.pack.theme.NeonVoidTheme.INSTANCE.properties().isEmpty(),
                "Variant changed nothing visible; it returns with the Stage 5 art");
        Map<String, String> lang = TestFiles.lang();
        assertEquals("Slow-down", lang.get(za.co.neroland.neroarcade.module.pack.pace.SteadyPace.INSTANCE
                .properties().get(0).langKey(za.co.neroland.neroarcade.module.pack.pace.SteadyPace.INSTANCE)));
        List<String> legend = za.co.neroland.neroarcade.module.pack.control.FourWayControl.INSTANCE
                .legend(PropertyValues.defaults(za.co.neroland.neroarcade.module.pack.control.FourWayControl.INSTANCE));
        assertEquals(List.of("neroarcade.legend.move_4way"), legend, "the keycap line already names the buttons");
        assertFalse(lang.containsKey("neroarcade.legend.button_a"));
        assertFalse(lang.containsKey("neroarcade.legend.button_b"));
    }

    @Test
    void noCommercialGameNames() {
        // each line is a regex with a one-character class, so the public repo never spells a name out
        List<java.util.regex.Pattern> blocked = TestFiles.read(TestFiles.testResources().resolve("blocked_names.txt"))
                .lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .map(l -> java.util.regex.Pattern.compile(l, java.util.regex.Pattern.CASE_INSENSITIVE)).toList();
        assertEquals(17, blocked.size(), "every blocked name is loaded");
        assertTrue(blocked.get(0).matcher("T" + "ETRIS").find(), "the patterns match the real spelling");
        Map<String, String> lang = TestFiles.lang();
        for (ArcadeModule module : ModuleRegistry.all()) {
            String text = (module.id() + " " + lang.getOrDefault(module.langKey(), "") + " "
                    + lang.getOrDefault(module.descLangKey(), "")).toLowerCase(Locale.ROOT);
            for (java.util.regex.Pattern name : blocked) {
                assertFalse(name.matcher(text).find(), module.id() + " mentions " + name.pattern());
            }
        }
        lang.forEach((key, value) -> blocked.forEach(name ->
                assertFalse(name.matcher(value).find(), key + " mentions " + name.pattern())));
    }
}
