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
    void noCommercialGameNames() {
        List<String> blocked = TestFiles.read(TestFiles.testResources().resolve("blocked_names.txt")).lines()
                .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        Map<String, String> lang = TestFiles.lang();
        for (ArcadeModule module : ModuleRegistry.all()) {
            String text = (module.id() + " " + lang.getOrDefault(module.langKey(), "") + " "
                    + lang.getOrDefault(module.descLangKey(), "")).toLowerCase(Locale.ROOT);
            for (String name : blocked) {
                assertFalse(text.contains(name), module.id() + " mentions " + name);
            }
        }
        lang.forEach((key, value) -> blocked.forEach(name ->
                assertFalse(value.toLowerCase(Locale.ROOT).contains(name), key + " mentions " + name)));
    }
}
