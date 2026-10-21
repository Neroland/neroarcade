package za.co.neroland.neroarcade.client.bench;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.BenchWarning;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * The words of the "Build your game" screen, separated from drawing so a test can prove the "no manual needed"
 * rule: every rack row has a label, a one-line effect or an empty-slot sentence, every warning lands on a row,
 * and every disabled button has a one-sentence reason — each a lang key the shipped {@code en_us.json} defines.
 * Pure.
 */
public final class BenchModel {

    /** The screen's buttons. */
    public enum Button { TEST_PLAY, STOP_TEST, SAVE, LOAD, CLEAR }

    /**
     * What the buttons depend on.
     *
     * @param testing        a test play is running on this bench
     * @param canSave        the tray holds a Blank Cartridge or a cartridge (the server's {@code canSave})
     * @param trayHasGame    the tray holds a written cartridge (LOAD can read it)
     * @param titleBlank     no title typed yet
     * @param draftRefused   this server's validator refuses the draft (the status bar says why)
     */
    public record Context(boolean testing, boolean canSave, boolean trayHasGame, boolean titleBlank,
            boolean draftRefused) {
    }

    /** Every disabled-state reason key, for the lang test. */
    public static final List<String> WHY_KEYS = List.of(
            "neroarcade.bench.why.testing", "neroarcade.bench.why.need_blank", "neroarcade.bench.why.need_title",
            "neroarcade.bench.why.invalid", "neroarcade.bench.why.need_cartridge", "neroarcade.bench.why.cannot_run");

    /** The 5x5 slot glyphs of 004 §2 ({@code #} lit), one per {@link ModuleType}, in order. */
    private static final String[][] GLYPHS = {
        {"#####", "#...#", "#...#", "#...#", "#####"},
        {"..#..", ".###.", "..#..", ".#.#.", "#...#"},
        {"#####", "#..##", "#.#.#", "##..#", "#####"},
        {"..#..", "..#..", ".#.#.", ".#.#.", "#####"},
        {"..#..", ".###.", "#####", ".###.", "..#.."},
        {"..#..", "..#..", "#####", "..#..", "..#.."},
        {"#....", "##...", "###..", "##...", "#...."},
        {".....", "#.#..", "#.#.#", "..#.#", "....."},
        {".#.#.", "###.#", ".#.#.", "#.###", ".#.#."},
        {"..#..", ".###.", "#####", "#####", ".###."},
    };

    private BenchModel() {
    }

    /** Whether glyph cell {@code (x, y)} (0..4) of a slot is lit. */
    public static boolean glyph(ModuleType type, int x, int y) {
        return GLYPHS[type.ordinal()][y].charAt(x) == '#';
    }

    /** The slot's word ("Arena"). */
    public static String slotKey(ModuleType type) {
        return type.langKey();
    }

    /** One line: what the chip in the slot does. */
    public static String effectKey(ArcadeModule module) {
        return module.descLangKey();
    }

    /**
     * The grey sentence of an empty slot: "Empty — uses Grid 16" for slots with a default ({@link #defaultName}
     * fills the argument), a plain sentence for HAZARD, PICKUP and MODIFIER, which have none.
     */
    public static String emptyKey(ModuleType type) {
        return switch (type) {
            case HAZARD -> "neroarcade.bench.empty.hazard";
            case PICKUP -> "neroarcade.bench.empty.pickup";
            case MODIFIER -> "neroarcade.bench.empty.modifier";
            default -> "neroarcade.bench.empty_default";
        };
    }

    /** The lang key of the module an empty slot falls back to, or empty for HAZARD/PICKUP/MODIFIER. */
    public static Optional<String> defaultName(ModuleType type) {
        Optional<Identifier> id = ModuleDefaults.defaultFor(type);
        return id.map(i -> "module.neroarcade." + i.getPath().replace('/', '.'));
    }

    /** The rack row a warning belongs to (shown in grey on that row); null = the whole game (status bar only). */
    public static @Nullable ModuleType rowOf(BenchWarning warning) {
        return switch (warning) {
            case SCORE_RULE_WITHOUT_SCORE_SOURCE, CLEAR_BOARD_WITHOUT_PICKUPS -> ModuleType.RULE;
            case KEY_WITHOUT_EXIT, EXIT_ON_ONE_AXIS -> ModuleType.PICKUP;
            case AIM_WITHOUT_FIRING_PAWN, ONE_BUTTON_FREE_PAWN, TWO_WAY_ON_FREE_PAWN -> ModuleType.CONTROL;
            case VERSUS_NEEDS_SEATS -> ModuleType.MULTIPLAYER;
            case NO_THREAT, CROWDED -> ModuleType.HAZARD;
            case UNKNOWN_PROPERTY -> null;
        };
    }

    /** Why a button is disabled (a lang key), or null when it is enabled. */
    public static @Nullable String whyDisabled(Button button, Context c) {
        return switch (button) {
            case TEST_PLAY -> c.draftRefused() ? "neroarcade.bench.why.cannot_run" : null;
            case STOP_TEST -> null;
            case SAVE -> {
                if (c.testing()) {
                    yield "neroarcade.bench.why.testing";
                }
                if (!c.canSave()) {
                    yield "neroarcade.bench.why.need_blank";
                }
                if (c.titleBlank()) {
                    yield "neroarcade.bench.why.need_title";
                }
                yield c.draftRefused() ? "neroarcade.bench.why.invalid" : null;
            }
            case LOAD -> {
                if (c.testing()) {
                    yield "neroarcade.bench.why.testing";
                }
                yield c.trayHasGame() ? null : "neroarcade.bench.why.need_cartridge";
            }
            case CLEAR -> c.testing() ? "neroarcade.bench.why.testing" : null;
        };
    }

    /** What an enabled button does (hover help). */
    public static String helpKey(Button button) {
        return "neroarcade.bench.help." + button.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Status keys that report success (green); every other non-empty status is a refusal (red, deny sound). */
    public static boolean isSuccess(String statusKey) {
        return statusKey.equals("neroarcade.bench.status.saved") || statusKey.equals("neroarcade.bench.status.loaded")
                || statusKey.equals("neroarcade.bench.status.test_started");
    }
}
