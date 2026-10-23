package za.co.neroland.neroarcade.module.pack.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

class RulePackTest {

    private static final List<ArcadeModule> PACK = List.of(ClearBoardRule.INSTANCE, LastStandingRule.INSTANCE,
            ReachExitRule.INSTANCE, ScoreTargetRule.INSTANCE);

    @BeforeEach
    void registry() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        RulePack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    /** WP-B's lang fragment (merged into the shipped file by the integration step). */
    static Map<String, String> fragment() {
        Map<String, String> out = new TreeMap<>();
        String text = TestFiles.read(TestFiles.repoRoot().resolve("tools/lang/wp_b.en_us.json"));
        for (Map.Entry<String, JsonElement> e : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
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
    void registersEveryRuleOnce() {
        List<ArcadeModule> rules = ModuleRegistry.byType(ModuleType.RULE);
        assertEquals(5, rules.size(), "survive_time + four");
        for (ArcadeModule module : PACK) {
            assertTrue(rules.contains(module), module.id().toString());
            assertTrue(module instanceof RuleModule);
            assertEquals("neroarcade", module.id().getNamespace());
        }
        List<String> ids = PACK.stream().map(m -> m.id().getPath()).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "pack order is alphabetical");
    }

    @Test
    void flagsAreAsSpecified() {
        assertEquals(java.util.Set.of(ModuleFlag.NEEDS_SCORE), ScoreTargetRule.INSTANCE.flags());
        assertEquals(java.util.Set.of(ModuleFlag.NEEDS_CLEARABLE), ClearBoardRule.INSTANCE.flags());
        assertEquals(java.util.Set.of(ModuleFlag.PLACES_EXIT, ModuleFlag.NEEDS_KEY), ReachExitRule.INSTANCE.flags());
        assertEquals(java.util.Set.of(ModuleFlag.MULTI_SEAT_ONLY), LastStandingRule.INSTANCE.flags());
    }

    @Test
    void langEffectAndHudLinesFit() {
        Map<String, String> lang = fragment();
        for (ArcadeModule module : PACK) {
            RuleModule rule = (RuleModule) module;
            PropertyValues defaults = PropertyValues.defaults(module);
            assertTrue(lang.containsKey(module.langKey()), module.langKey());
            String desc = lang.get(module.descLangKey());
            assertTrue(desc != null && desc.length() <= 70, module.descLangKey());
            for (PropertySpec spec : module.properties()) {
                assertTrue(lang.containsKey(spec.langKey(module)), spec.langKey(module));
            }
            String effect = render(lang, module.effectLangKey(), module.effectArgs(defaults));
            assertTrue(effect.length() <= 22, module.id() + " effect: " + effect);
            Object[] hud = java.util.Arrays.stream(rule.hudArgs(defaults)).boxed().toArray();
            String line = render(lang, rule.hudLangKey(), hud);
            assertTrue(line.length() <= 24, module.id() + " hud: " + line);
            assertTrue(!effect.contains("%") && !line.contains("%"));
        }
        assertEquals("Score 100 · lives 3", render(lang, ScoreTargetRule.INSTANCE.effectLangKey(),
                ScoreTargetRule.INSTANCE.effectArgs(PropertyValues.defaults(ScoreTargetRule.INSTANCE))));
    }
}
