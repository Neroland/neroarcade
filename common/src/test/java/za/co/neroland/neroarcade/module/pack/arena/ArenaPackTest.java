package za.co.neroland.neroarcade.module.pack.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

/** 008 §1/§3: the ARENA pack registers, has its lang lines, its effect fits, and every chip has a recipe. */
class ArenaPackTest {

    private static final List<ArcadeModule> PACK = List.of(Grid32Arena.INSTANCE, Ring24Arena.INSTANCE, Strip9Arena.INSTANCE);

    @BeforeEach
    void registry() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        ArenaPack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    static Map<String, String> lang() {
        Map<String, String> out = new TreeMap<>();
        for (String file : new String[] {"tools/lang/modules.en_us.json", "tools/lang/wp_a.en_us.json"}) {
            String text = TestFiles.read(TestFiles.repoRoot().resolve(file));
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    static String render(Map<String, String> lang, String key, Object... args) {
        assertTrue(lang.containsKey(key), "missing " + key);
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component c && c.getContents() instanceof TranslatableContents t) {
                assertTrue(lang.containsKey(t.getKey()), "missing " + t.getKey());
                plain[i] = lang.get(t.getKey());
            } else {
                plain[i] = String.valueOf(args[i]);
            }
        }
        return String.format(Locale.ROOT, lang.get(key), plain);
    }

    @Test
    void registersEveryArenaOnceInIdOrder() {
        List<String> ids = ModuleRegistry.byType(ModuleType.ARENA).stream().map(m -> m.id().getPath()).toList();
        assertEquals(List.of("arena/grid_16", "arena/grid_32", "arena/ring_24", "arena/strip_9"), ids);
    }

    @Test
    void langEffectAndRecipeForEveryChip() throws Exception {
        Map<String, String> lang = lang();
        for (ArcadeModule module : PACK) {
            assertTrue(lang.containsKey(module.langKey()), module.langKey());
            String desc = lang.get(module.descLangKey());
            assertTrue(desc != null && desc.length() <= 70 && desc.endsWith("."), module.descLangKey());
            for (PropertySpec spec : module.properties()) {
                assertTrue(lang.containsKey(spec.langKey(module)), spec.langKey(module));
                for (String constant : spec.constants()) {
                    assertTrue(lang.containsKey(spec.constantLangKey(module, constant)), spec.constantLangKey(module, constant));
                }
            }
            String effect = render(lang, module.effectLangKey(), module.effectArgs(PropertyValues.defaults(module)));
            assertTrue(effect.length() <= 22 && !effect.contains("%"), module.id() + ": " + effect);

            String name = module.id().getPath().replace('/', '_');
            JsonObject recipe = JsonParser.parseString(TestFiles.read(TestFiles.repoRoot()
                    .resolve("common/src/main/resources/data/neroarcade/recipe/module_chip_" + name + ".json"))).getAsJsonObject();
            JsonObject components = recipe.getAsJsonObject("result").getAsJsonObject("components");
            assertEquals(module.id().toString(), components.get("neroarcade:module_id").getAsString());
            assertEquals("arena", components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("strings")
                    .get(0).getAsString());
            assertEquals(3138559, components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("colors")
                    .get(0).getAsInt());
            assertEquals(3, recipe.getAsJsonArray("ingredients").size());
            assertEquals("minecraft:glass_pane", recipe.getAsJsonArray("ingredients").get(1).getAsString());
        }
    }
}
