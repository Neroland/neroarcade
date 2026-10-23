package za.co.neroland.neroarcade.module.pack.modifier;

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
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;

class ModifierPackTest {

    private static final List<ArcadeModule> PACK = List.of(FogModifier.INSTANCE, LowGravityModifier.INSTANCE,
            MirrorModifier.INSTANCE, WrapAroundModifier.INSTANCE);

    @BeforeEach
    void registry() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        ModifierPack.registerAll();
        ModuleRegistry.freeze();
    }

    @AfterEach
    void restore() {
        TestModules.install();
    }

    private static Map<String, String> fragment() {
        Map<String, String> out = new TreeMap<>();
        String text = TestFiles.read(TestFiles.repoRoot().resolve("tools/lang/wp_b.en_us.json"));
        for (Map.Entry<String, JsonElement> e : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
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
    void registersEveryModifierOnce() {
        List<ArcadeModule> modifiers = ModuleRegistry.byType(ModuleType.MODIFIER);
        assertEquals(4, modifiers.size(), "the slot has no default");
        for (ArcadeModule module : PACK) {
            assertTrue(modifiers.contains(module), module.id().toString());
            assertTrue(module instanceof ModifierModule);
            assertTrue(module.flags().isEmpty());
        }
        List<String> ids = PACK.stream().map(m -> m.id().getPath()).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
    }

    @Test
    void langEffectLinesFit() throws za.co.neroland.neroarcade.game.ValidationError {
        Map<String, String> lang = fragment();
        for (ArcadeModule module : PACK) {
            PropertyValues defaults = PropertyValues.defaults(module);
            assertTrue(lang.containsKey(module.langKey()));
            assertTrue(lang.get(module.descLangKey()).length() <= 70);
            String effect = render(lang, module.effectLangKey(), module.effectArgs(defaults));
            assertTrue(effect.length() <= 22, module.id() + ": " + effect);
            assertTrue(!effect.contains("%"), module.id() + " left a placeholder: " + effect);
            for (PropertySpec spec : module.properties()) {
                assertTrue(lang.containsKey(spec.langKey(module)), spec.langKey(module));
                for (String constant : spec.constants()) {
                    assertTrue(lang.containsKey(spec.constantLangKey(module, constant)));
                    String at = render(lang, module.effectLangKey(),
                            module.effectArgs(defaults.with(spec.name(), new PropertyValue.EnumValue(constant))));
                    assertTrue(at.length() <= 24, module.id() + " " + constant + ": " + at);
                }
                if (spec.constants().isEmpty()) {
                    for (int v : new int[] {spec.min(), spec.max()}) {
                        String at = render(lang, module.effectLangKey(),
                                module.effectArgs(defaults.with(spec.name(), new PropertyValue.IntValue(v))));
                        assertTrue(at.length() <= 24, module.id() + " " + spec.name() + "=" + v + ": " + at);
                    }
                }
            }
        }
        assertEquals("Low gravity ×0.50", render(lang, LowGravityModifier.INSTANCE.effectLangKey(),
                LowGravityModifier.INSTANCE.effectArgs(PropertyValues.defaults(LowGravityModifier.INSTANCE))));
    }
}
