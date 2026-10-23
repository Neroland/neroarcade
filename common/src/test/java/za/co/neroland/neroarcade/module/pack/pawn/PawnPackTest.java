package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

/** 008 §1/§2.2/§3: the PAWN pack registers, declares 008's flags, has its lang lines and its recipes. */
class PawnPackTest {

    private static final List<ArcadeModule> PACK = List.of(PaddlePawn.INSTANCE, RunnerPawn.INSTANCE,
            SerpentPawn.INSTANCE, ShipPawn.INSTANCE, StackerPawn.INSTANCE);

    private static final Map<String, String> THIRD = Map.of(
            "paddle", "minecraft:stick",
            "runner", "minecraft:leather_boots",
            "serpent", "minecraft:slime_ball",
            "ship", "minecraft:arrow",
            "stacker", "minecraft:brick");

    @BeforeEach
    void registry() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        PawnPack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    private static Map<String, String> lang() {
        Map<String, String> out = new TreeMap<>();
        for (String file : new String[] {"tools/lang/modules.en_us.json", "tools/lang/wp_a.en_us.json"}) {
            String text = TestFiles.read(TestFiles.repoRoot().resolve(file));
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    private static String render(Map<String, String> lang, String key, Object... args) {
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
    void registersEveryPawnOnceInIdOrder() {
        List<String> ids = ModuleRegistry.byType(ModuleType.PAWN).stream().map(m -> m.id().getPath()).toList();
        assertEquals(List.of("pawn/cursor", "pawn/paddle", "pawn/runner", "pawn/serpent", "pawn/ship", "pawn/stacker"), ids);
    }

    @Test
    void flagsAreThoseOf008() {
        assertEquals(Set.of(), CursorPawn.INSTANCE.flags());
        assertEquals(Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.LETHAL_TO_PAWNS), SerpentPawn.INSTANCE.flags());
        assertEquals(Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.ONE_AXIS), RunnerPawn.INSTANCE.flags());
        assertEquals(Set.of(ModuleFlag.ONE_AXIS), PaddlePawn.INSTANCE.flags());
        assertEquals(Set.of(ModuleFlag.FIRES), ShipPawn.INSTANCE.flags());
        // 008 §8.2 approved: the stacker scores on landings and row clears, so it declares SCORES
        assertEquals(Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.SCORES), StackerPawn.INSTANCE.flags());
    }

    @Test
    void langEffectAndRecipeForEveryChip() throws Exception {
        Map<String, String> lang = lang();
        for (ArcadeModule module : PACK) {
            assertTrue(lang.containsKey(module.langKey()), module.langKey());
            String desc = lang.get(module.descLangKey());
            assertTrue(desc != null && desc.length() <= 70 && desc.endsWith("."), module.descLangKey());
            assertTrue(module.properties().size() <= 3, module.id().toString());
            for (PropertySpec spec : module.properties()) {
                assertTrue(lang.containsKey(spec.langKey(module)), spec.langKey(module));
                for (String constant : spec.constants()) {
                    assertTrue(lang.containsKey(spec.constantLangKey(module, constant)), spec.constantLangKey(module, constant));
                }
            }
            PropertyValues defaults = PropertyValues.defaults(module);
            String effect = render(lang, module.effectLangKey(), module.effectArgs(defaults));
            assertTrue(effect.length() <= 22 && !effect.contains("%"), module.id() + ": " + effect);

            String name = module.id().getPath().substring("pawn/".length());
            JsonObject recipe = JsonParser.parseString(TestFiles.read(TestFiles.repoRoot()
                    .resolve("common/src/main/resources/data/neroarcade/recipe/module_chip_pawn_" + name + ".json")))
                    .getAsJsonObject();
            JsonObject components = recipe.getAsJsonObject("result").getAsJsonObject("components");
            assertEquals(module.id().toString(), components.get("neroarcade:module_id").getAsString());
            assertEquals("pawn", components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("strings")
                    .get(0).getAsString());
            assertEquals(16727972, components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("colors")
                    .get(0).getAsInt());
            assertEquals(List.of("neroarcade:blank_chip", "#nerolandcore:materials/nero_alloy", THIRD.get(name)),
                    recipe.getAsJsonArray("ingredients").asList().stream().map(JsonElement::getAsString).toList());
        }
    }
}
