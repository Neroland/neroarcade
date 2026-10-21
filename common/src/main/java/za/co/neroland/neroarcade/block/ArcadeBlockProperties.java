package za.co.neroland.neroarcade.block;

import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Every blockstate property NeroArcade's blocks declare, in one plain class so the asset contract can be
 * checked without bootstrapping Minecraft ({@code AssetContractTest} compares {@link #BY_BLOCK} with the
 * blockstate JSONs). Names and values are the {@code 004} "Registry ids" contract verbatim.
 */
public final class ArcadeBlockProperties {

    /*
     * Built here rather than taken from BlockStateProperties: that class also creates the note-block instrument
     * property, whose enum reaches the sound registry, so merely loading it needs a bootstrapped game. These are
     * equal in name and values to the vanilla ones; a block only ever compares its own properties.
     */
    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class, Direction.Plane.HORIZONTAL);
    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
    public static final EnumProperty<CabinetStatus> STATUS = EnumProperty.create("status", CabinetStatus.class);
    public static final EnumProperty<PadDirection> PAD = EnumProperty.create("pad", PadDirection.class);
    public static final BooleanProperty BTN_A = BooleanProperty.create("btn_a");
    public static final BooleanProperty BTN_B = BooleanProperty.create("btn_b");
    public static final BooleanProperty CARTRIDGE = BooleanProperty.create("cartridge");
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    /** Game bench: a player has the bench open (BER-only, no model variant). */
    public static final BooleanProperty EDITING = BooleanProperty.create("editing");
    public static final BooleanProperty COPYING = BooleanProperty.create("copying");
    public static final BooleanProperty ORIGINAL = BooleanProperty.create("original");
    public static final BooleanProperty BLANK = BooleanProperty.create("blank");
    /** Marquee scale 1..4 (BER-only, no model variant). */
    public static final IntegerProperty SCALE = IntegerProperty.create("scale", 1, 4);
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Block path → the properties that block declares, in declaration order. */
    public static final Map<String, List<Property<?>>> BY_BLOCK = Map.of(
            "arcade_cabinet", List.of(FACING, HALF, STATUS, PAD, BTN_A, BTN_B, CARTRIDGE, LINKED),
            "game_bench", List.of(FACING, CARTRIDGE, EDITING),
            "cartridge_duplicator", List.of(FACING, COPYING, ORIGINAL, BLANK),
            "arcade_exchange", List.of(FACING, HALF),
            "marquee_screen", List.of(FACING, SCALE),
            "link_bus", List.of(FACING),
            "link_cable", List.of(NORTH, SOUTH, EAST, WEST, UP, DOWN, ACTIVE));

    private ArcadeBlockProperties() {
    }
}
