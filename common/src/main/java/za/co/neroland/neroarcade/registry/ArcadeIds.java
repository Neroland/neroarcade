package za.co.neroland.neroarcade.registry;

import java.util.List;

/**
 * Every registry path NeroArcade owns, as plain strings, so data files can be checked against them in a plain
 * JVM ({@code RecipeFilesTest}, {@code AssetContractTest}). The registration classes use these same names.
 */
public final class ArcadeIds {

    public static final String ARCADE_CABINET = "arcade_cabinet";
    public static final String GAME_BENCH = "game_bench";
    public static final String CARTRIDGE_DUPLICATOR = "cartridge_duplicator";
    public static final String ARCADE_EXCHANGE = "arcade_exchange";
    public static final String MARQUEE_SCREEN = "marquee_screen";
    public static final String LINK_BUS = "link_bus";
    public static final String LINK_CABLE = "link_cable";

    public static final String MODULE_CHIP = "module_chip";
    public static final String CARTRIDGE = "cartridge";
    public static final String BLANK_CARTRIDGE = "blank_cartridge";
    public static final String BLANK_CHIP = "blank_chip";

    /** Every block, in creative-tab order. Each has a block item of the same name. */
    public static final List<String> BLOCKS = List.of(ARCADE_CABINET, GAME_BENCH, CARTRIDGE_DUPLICATOR,
            ARCADE_EXCHANGE, MARQUEE_SCREEN, LINK_BUS, LINK_CABLE);

    /** Every item, block items included. */
    public static final List<String> ITEMS = List.of(ARCADE_CABINET, GAME_BENCH, CARTRIDGE_DUPLICATOR,
            ARCADE_EXCHANGE, MARQUEE_SCREEN, LINK_BUS, LINK_CABLE, MODULE_CHIP, CARTRIDGE, BLANK_CARTRIDGE,
            BLANK_CHIP);

    /** Sound event keys ({@code sounds.json}); registered as {@code neroarcade:<key>}. */
    public static final List<String> SOUNDS = List.of(
            "cabinet.insert", "cabinet.start", "cabinet.deny", "cabinet.win", "cabinet.lose",
            "game.pickup", "game.hit", "game.tick", "bench.save", "duplicator.spin", "exchange.publish",
            "theme.neon_void.start", "theme.neon_void.loop", "theme.neon_void.end",
            "theme.hangar.start", "theme.hangar.loop", "theme.hangar.end",
            "theme.cindara_dusk.start", "theme.cindara_dusk.loop", "theme.cindara_dusk.end",
            "theme.terminal_green.start", "theme.terminal_green.loop", "theme.terminal_green.end");

    /** Data component paths. */
    public static final List<String> COMPONENTS = List.of("module_id", "game_def");

    private ArcadeIds() {
    }
}
