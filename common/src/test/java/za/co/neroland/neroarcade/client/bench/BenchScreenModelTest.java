package za.co.neroland.neroarcade.client.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.BenchWarning;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The "no manual needed" rule for the Game Bench, checked against the shipped {@code en_us.json}: every rack row
 * has a word, a purpose and an effect or empty-slot sentence; every warning lands somewhere visible; every disabled
 * button has a one-sentence reason in every state that disables it; every enabled button and control says what it
 * does; every key mapping is named.
 */
class BenchScreenModelTest {

    private static Map<String, String> lang;

    @BeforeAll
    static void setUp() {
        TestModules.installShipped();
        lang = TestFiles.lang();
    }

    private static void defined(String key) {
        assertTrue(lang.containsKey(key), "missing lang key " + key);
    }

    @Test
    void everyRowHasWordPurposeAndEmptySentence() {
        for (ModuleType type : ModuleType.values()) {
            defined(BenchModel.slotKey(type));
            defined(type.descLangKey());
            defined(BenchModel.emptyKey(type));
            Optional<String> fallback = BenchModel.defaultName(type);
            boolean hasDefault = type != ModuleType.HAZARD && type != ModuleType.PICKUP && type != ModuleType.MODIFIER;
            assertEquals(hasDefault, fallback.isPresent(), type + ": the empty sentence names the default");
            fallback.ifPresent(BenchScreenModelTest::defined);
            if (hasDefault) {
                assertTrue(lang.get(BenchModel.emptyKey(type)).contains("%s"), "\"Empty — uses <default>\"");
            }
        }
    }

    @Test
    void everyShippedChipHasAnEffectAndNamedSettings() {
        for (ArcadeModule module : ModuleRegistry.all()) {
            defined(module.langKey());
            defined(BenchModel.effectKey(module));
            for (PropertySpec spec : module.properties()) {
                defined(spec.langKey(module));
                defined("neroarcade.bench.help.prop." + spec.kind().name().toLowerCase(java.util.Locale.ROOT));
                for (String constant : spec.constants()) {
                    defined(spec.constantLangKey(module, constant));
                }
            }
        }
    }

    /** A lang value with its arguments filled the way the client would (translatable arguments looked up). */
    static String render(String key, Object... args) {
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof net.minecraft.network.chat.Component c
                    && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
                assertTrue(lang.containsKey(t.getKey()), "missing lang key " + t.getKey());
                plain[i] = lang.get(t.getKey());
            } else {
                plain[i] = String.valueOf(args[i]);
            }
        }
        return String.format(java.util.Locale.ROOT, lang.get(key), plain);
    }

    @Test
    void everyRowEffectIsShortWithLiveValues() throws za.co.neroland.neroarcade.game.ValidationError {
        for (ArcadeModule module : ModuleRegistry.all()) {
            za.co.neroland.neroarcade.module.PropertyValues defaults =
                    za.co.neroland.neroarcade.module.PropertyValues.defaults(module);
            String line = render(BenchModel.effectKey(module), module.effectArgs(defaults));
            assertTrue(line.length() <= 22, module.id() + " effect is over 22 characters: " + line);
            assertTrue(!line.contains("%"), module.id() + " effect left a placeholder: " + line);
            for (PropertySpec spec : module.properties()) {
                if (spec.kind() == za.co.neroland.neroarcade.module.PropertyKind.INT) {
                    for (int v : new int[] {spec.min(), spec.max()}) {
                        String at = render(BenchModel.effectKey(module), module.effectArgs(defaults.with(spec.name(),
                                new za.co.neroland.neroarcade.module.PropertyValue.IntValue(v))));
                        // 22 with defaults is the contract; extremes may reach the single row's ~24 characters
                        assertTrue(at.length() <= 24, module.id() + " at " + spec.name() + "=" + v + ": " + at);
                    }
                }
            }
        }
        za.co.neroland.neroarcade.module.PropertyValues rule = za.co.neroland.neroarcade.module.PropertyValues
                .defaults(ModuleRegistry.get(za.co.neroland.neroarcade.module.ModuleDefaults.RULE).orElseThrow());
        assertEquals("Survive 60 s · lives 1", render(BenchModel.effectKey(ModuleRegistry
                .get(za.co.neroland.neroarcade.module.ModuleDefaults.RULE).orElseThrow()),
                ModuleRegistry.get(za.co.neroland.neroarcade.module.ModuleDefaults.RULE).orElseThrow().effectArgs(rule)));
        assertEquals("Empty — no threats", lang.get(BenchModel.emptyKey(ModuleType.HAZARD)));
        assertEquals("Empty — no pickups", lang.get(BenchModel.emptyKey(ModuleType.PICKUP)));
    }

    @Test
    void noLineSaysPressAAndEveryKeyNameIsFilledIn() {
        assertEquals("Press %s to play again", lang.get("neroarcade.hud.play_again"));
        assertEquals("Press %s when you are ready", lang.get("neroarcade.hud.press_start"));
        assertTrue(lang.get("neroarcade.bench.testing_over").contains("%s"));
        assertEquals("2nd action", lang.get("neroarcade.hud.legend.b"));
        assertEquals("%s leave", lang.get("neroarcade.hud.leave_hint"));
        assertEquals(5, lang.get(BenchModel.TESTING_KEYS).split("%s", -1).length - 1, "up, left, down, right, A");
        assertEquals(5, lang.get(BenchModel.helpKey(BenchModel.Button.TEST_PLAY)).split("%s", -1).length - 1);
        lang.forEach((key, value) -> {
            if (key.startsWith("neroarcade.hud.") || key.startsWith("neroarcade.bench.") || key.startsWith("neroarcade.legend.")) {
                assertTrue(!value.matches(".*\\b(press|Press) A\\b.*") && !value.matches(".*\\bA plays\\b.*")
                        && !value.startsWith("A: ") && !value.startsWith("B: "), key + " names a key letter: " + value);
            }
        });
    }

    @Test
    void theRuleChipWritesTheHudLine() throws za.co.neroland.neroarcade.game.ValidationError {
        for (ArcadeModule module : ModuleRegistry.all()) {
            if (module instanceof za.co.neroland.neroarcade.module.hook.RuleModule rule) {
                int[] args = rule.hudArgs(za.co.neroland.neroarcade.module.PropertyValues.defaults(rule));
                Object[] boxed = java.util.Arrays.stream(args).boxed().toArray();
                defined(rule.hudLangKey());
                String line = render(rule.hudLangKey(), boxed);
                assertTrue(line.length() <= 24, rule.id() + " HUD line is over 24 characters: " + line);
                assertTrue(!line.contains("%"), line);
            }
        }
    }

    @Test
    void everyWarningIsWrittenAndPlaced() {
        int general = 0;
        for (BenchWarning warning : BenchWarning.values()) {
            defined(warning.langKey());
            if (BenchModel.rowOf(warning) == null) {
                general++;
            }
        }
        assertEquals(1, general, "only UNKNOWN_PROPERTY is about the whole game (status bar)");
    }

    @Test
    void everyDisabledStateHasAReasonAndEveryButtonAHelpLine() {
        for (String key : BenchModel.WHY_KEYS) {
            defined(key);
        }
        defined("neroarcade.bench.why.waiting");
        for (int bits = 0; bits < 32; bits++) {
            BenchModel.Context c = new BenchModel.Context((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0);
            for (BenchModel.Button button : BenchModel.Button.values()) {
                String why = BenchModel.whyDisabled(button, c);
                if (why != null) {
                    defined(why);
                    assertTrue(BenchModel.WHY_KEYS.contains(why), why + " is a listed reason");
                    assertTrue(lang.get(why).endsWith("."), "a reason is one sentence: " + lang.get(why));
                }
            }
        }
        for (BenchModel.Button button : BenchModel.Button.values()) {
            defined(BenchModel.helpKey(button));
        }
    }

    @Test
    void theSpecifiedReasonsAppearWhenExpected() {
        BenchModel.Context noBlank = new BenchModel.Context(false, false, false, false, false);
        assertEquals("neroarcade.bench.why.need_blank", BenchModel.whyDisabled(BenchModel.Button.SAVE, noBlank));
        assertEquals("Put a Blank Cartridge in the tray to save.", lang.get("neroarcade.bench.why.need_blank"));
        BenchModel.Context testing = new BenchModel.Context(true, true, true, false, false);
        assertEquals("neroarcade.bench.why.testing", BenchModel.whyDisabled(BenchModel.Button.SAVE, testing));
        assertEquals("neroarcade.bench.why.testing", BenchModel.whyDisabled(BenchModel.Button.LOAD, testing));
        assertNull(BenchModel.whyDisabled(BenchModel.Button.STOP_TEST, testing), "STOP is always available");
        BenchModel.Context ready = new BenchModel.Context(false, true, true, false, false);
        assertNull(BenchModel.whyDisabled(BenchModel.Button.SAVE, ready));
        assertNull(BenchModel.whyDisabled(BenchModel.Button.LOAD, ready));
        assertNull(BenchModel.whyDisabled(BenchModel.Button.TEST_PLAY, ready));
        BenchModel.Context untitled = new BenchModel.Context(false, true, false, true, false);
        assertEquals("neroarcade.bench.why.need_title", BenchModel.whyDisabled(BenchModel.Button.SAVE, untitled));
        assertEquals("neroarcade.bench.why.need_cartridge", BenchModel.whyDisabled(BenchModel.Button.LOAD, untitled));
        BenchModel.Context broken = new BenchModel.Context(false, true, false, false, true);
        assertNotNull(BenchModel.whyDisabled(BenchModel.Button.TEST_PLAY, broken));
    }

    @Test
    void screenWordsKeysAndGlyphsExist() {
        for (String key : new String[] {"container.neroarcade.game_bench", "neroarcade.bench.subtitle",
                "neroarcade.bench.rack", "neroarcade.bench.preview", "neroarcade.bench.parameters",
                "neroarcade.bench.ghost", "neroarcade.bench.ghost_tip", "neroarcade.bench.tray",
                "neroarcade.bench.tray_hint", "neroarcade.bench.tray_empty", "neroarcade.bench.tray_holds",
                "neroarcade.bench.title_label", "neroarcade.bench.title_hint", "neroarcade.bench.help.title",
                "neroarcade.bench.help.row", "neroarcade.bench.slot_tip", "neroarcade.bench.preview_caption",
                "neroarcade.bench.testing_caption", "neroarcade.bench.testing_over", "neroarcade.bench.params_empty",
                "neroarcade.bench.params_empty_default", "neroarcade.bench.params_none", "neroarcade.bench.params_more",
                "neroarcade.bench.status.waiting", "neroarcade.bench.status.hint", "neroarcade.bench.credit_off",
                "neroarcade.bench.button.test_play", "neroarcade.bench.button.stop_test", "neroarcade.bench.button.save",
                "neroarcade.bench.button.load", "key.category.neroarcade.arcade", "neroarcade.hud.legend.move",
                "neroarcade.hud.legend.a", "neroarcade.hud.legend.b", "neroarcade.hud.legend.leave"}) {
            defined(key);
        }
        for (String key : new String[] {"up", "down", "left", "right", "a", "b", "aim_up", "aim_down", "aim_left",
                "aim_right"}) {
            defined("key.neroarcade." + key);
        }
        for (ModuleType type : ModuleType.values()) {
            int lit = 0;
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    lit += BenchModel.glyph(type, x, y) ? 1 : 0;
                }
            }
            assertTrue(lit >= 5, type + " has a glyph");
        }
        assertTrue(BenchModel.isSuccess("neroarcade.bench.status.saved"));
        assertTrue(!BenchModel.isSuccess("neroarcade.bench.status.need_blank"));
    }

    @Test
    void previewIsCappedAndRestartsOnChange() {
        BenchPreview preview = new BenchPreview();
        assertEquals(0, preview.advance(5), "nothing to preview yet");
        preview.setGame(TestModules.resolvedOf());
        assertNotNull(preview.state());
        assertEquals(1, preview.restarts());
        assertTrue(preview.advance(1000) <= BenchPreview.MAX_STEPS_PER_CALL, "at most 20 steps per call");
        preview.setGame(TestModules.resolvedOf());
        assertEquals(2, preview.restarts(), "a new draft restarts the demo");
        assertEquals(SeatInput.of(SeatInput.RIGHT, 0, 0), BenchPreview.scripted(0));
        assertEquals(SeatInput.of(SeatInput.UP, 0, 0), BenchPreview.scripted(BenchPreview.LAP - 1));
    }
}
