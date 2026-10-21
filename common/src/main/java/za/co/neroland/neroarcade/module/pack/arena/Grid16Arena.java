package za.co.neroland.neroarcade.module.pack.arena;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/**
 * {@code neroarcade:arena/grid_16} — the default arena: a 16x16 open grid with a wall border. Obstacles:
 * {@code none}, {@code pillars} (a regular grid of single-cell pillars) or {@code maze_lite} (six short wall
 * bars from the match RNG, never touching the top-left spawn corner; other spawns move to the nearest empty
 * cell). Stateless; pure.
 */
public final class Grid16Arena implements ArenaModule {

    public static final Grid16Arena INSTANCE = new Grid16Arena();

    public static final int SIDE = 16;

    private static final List<PropertySpec> PROPERTIES =
            List.of(PropertySpec.enumeration("obstacles", "none", "none", "pillars", "maze_lite"));

    private Grid16Arena() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.ARENA;
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
                for (int bar = 0; bar < 6; bar++) {
                    int x = rng.nextInt(3, w - 4);
                    int y = rng.nextInt(3, h - 4);
                    boolean horizontal = rng.chance(1, 2);
                    for (int i = 0; i < 3; i++) {
                        state.setCell(horizontal ? x + i : x, horizontal ? y : y + i, Cell.WALL);
                    }
                }
            }
            default -> {
                // open grid
            }
        }
    }
}
