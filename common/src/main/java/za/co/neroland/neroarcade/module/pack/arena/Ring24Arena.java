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

/**
 * {@code neroarcade:arena/ring_24} — a 24x8 tube that wraps left to right (wrapsX) and not top to bottom: rows 0 and
 * 7 are WALL.
 * <ul>
 *   <li>{@code obstacles} — {@code none}, or {@code pillars}: two-cell WALL pillars at (x, 3) and (x, 4) for x in
 *       {2, 8, 14, 20}.</li>
 * </ul>
 * The runtime applies {@link #wraps} to both axes before {@code build}; {@code build} narrows it to X only with
 * {@code setWrap(true, false)} (a {@code wrap_around} modifier runs later and may widen it again). Spawns: four
 * points on the ring, {@code (3 + 6*seat, seat even ? 5 : 2)}, moved to the nearest EMPTY cell.
 *
 * <p>Odd combinations: a paddle on the ring sits on rows 5 / 2 with goal rows 6 / 1; the ball wraps on X and only
 * scores vertically. Sprites: tiles floor / floor_alt / wall / wall_top. Stateless; pure.
 */
public final class Ring24Arena implements ArenaModule {

    public static final Ring24Arena INSTANCE = new Ring24Arena();

    public static final int WIDTH = 24;
    public static final int HEIGHT = 8;

    private static final List<PropertySpec> PROPERTIES =
            List.of(PropertySpec.enumeration("obstacles", "none", "none", "pillars"));

    private Ring24Arena() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "arena/ring_24");
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
        return WIDTH;
    }

    @Override
    public int height(PropertyValues props) {
        return HEIGHT;
    }

    /** True: the ring wraps (on X only — see {@link #build}). */
    @Override
    public boolean wraps(PropertyValues props) {
        return true;
    }

    @Override
    public void build(ModuleContext ctx) {
        MatchState state = ctx.state();
        state.setWrap(true, false);
        int w = state.width();
        int h = state.height();
        for (int x = 0; x < w; x++) {
            state.setCell(x, 0, Cell.WALL);
            state.setCell(x, h - 1, Cell.WALL);
        }
        if ("pillars".equals(ctx.props().getEnum("obstacles"))) {
            for (int x = 2; x < w; x += 6) {
                state.setCell(x, 3, Cell.WALL);
                state.setCell(x, 4, Cell.WALL);
            }
        }
    }

    @Override
    public int[] spawnCell(ModuleContext ctx, int seat) {
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        int ax = Math.floorMod(3 + 6 * (seat & 3), w);
        int ay = Math.min(h - 1, (seat & 1) == 0 ? 5 : 2);
        int max = Math.max(w, h);
        for (int r = 0; r <= max; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = ax + dx;
                    int y = ay + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h && state.cell(x, y) == Cell.EMPTY) {
                        return new int[] {x, y};
                    }
                }
            }
        }
        return new int[] {ax, ay};
    }

    /** "24 by 8 ring · None": the obstacle layout's label. */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(PROPERTIES.get(0).constantLangKey(this, props.getEnum("obstacles")))};
    }
}
