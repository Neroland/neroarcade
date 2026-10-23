package za.co.neroland.neroarcade.module.pack.arena;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/**
 * {@code neroarcade:arena/grid_32} — a 32x32 open grid with a wall border, no wrap.
 * <ul>
 *   <li>{@code obstacles} — {@code none}; {@code pillars} (a single-cell pillar every 4 cells from (4, 4): 49
 *       pillars); {@code maze_lite} (24 wall bars of 4 cells from the match RNG, each starting at 3..27 on both
 *       axes so no bar touches the border or the top-left spawn corner).</li>
 * </ul>
 * Spawns use the default (inset corners, nearest EMPTY). Odd combinations: a 32x32 maze with a speed-1 cursor is
 * slow but legal; nothing is warned. Sprites: tiles floor / floor_alt / wall / wall_top. Stateless; pure.
 */
public final class Grid32Arena implements ArenaModule {

    public static final Grid32Arena INSTANCE = new Grid32Arena();

    public static final int SIDE = 32;

    /** Bars drawn by {@code maze_lite} and their length in cells. */
    static final int MAZE_BARS = 24;
    static final int MAZE_BAR_LENGTH = 4;

    private static final List<PropertySpec> PROPERTIES =
            List.of(PropertySpec.enumeration("obstacles", "none", "none", "pillars", "maze_lite"));

    private Grid32Arena() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "arena/grid_32");
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of();
    }

    @Override
    public int width(PropertyValues props) {
        return SIDE;
    }

    @Override
    public int height(PropertyValues props) {
        return SIDE;
    }

    @Override
    public boolean wraps(PropertyValues props) {
        return false;
    }

    @Override
    public void build(ModuleContext ctx) {
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        for (int x = 0; x < w; x++) {
            state.setCell(x, 0, Cell.WALL);
            state.setCell(x, h - 1, Cell.WALL);
        }
        for (int y = 0; y < h; y++) {
            state.setCell(0, y, Cell.WALL);
            state.setCell(w - 1, y, Cell.WALL);
        }
        switch (ctx.props().getEnum("obstacles")) {
            case "pillars" -> {
                for (int y = 4; y < h - 2; y += 4) {
                    for (int x = 4; x < w - 2; x += 4) {
                        state.setCell(x, y, Cell.WALL);
                    }
                }
            }
            case "maze_lite" -> {
                Rng rng = ctx.rng();
                for (int bar = 0; bar < MAZE_BARS; bar++) {
                    int x = rng.nextInt(3, w - 1 - MAZE_BAR_LENGTH);
                    int y = rng.nextInt(3, h - 1 - MAZE_BAR_LENGTH);
                    boolean horizontal = rng.chance(1, 2);
                    for (int i = 0; i < MAZE_BAR_LENGTH; i++) {
                        state.setCell(horizontal ? x + i : x, horizontal ? y : y + i, Cell.WALL);
                    }
                }
            }
            default -> {
                // open grid
            }
        }
    }

    /** "32 by 32 board · None": the obstacle layout's label. */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(PROPERTIES.get(0).constantLangKey(this, props.getEnum("obstacles")))};
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.grid}). */
    @Override
    public String genreTag() {
        return "grid";
    }
}
