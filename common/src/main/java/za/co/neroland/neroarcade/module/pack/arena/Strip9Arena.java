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
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/**
 * {@code neroarcade:arena/strip_9} — a 9-wide lane, {@code length} cells tall, no wrap. Every cell is LANE and
 * there are no wall cells at all: the board edge is the wall ({@code MatchState.cell} reads WALL out of range).
 * <ul>
 *   <li>{@code scroll} — {@code none} (static), {@code down} or {@code up}: every {@code period} ticks the rows
 *       shift one step that way and a fresh row is written at the far end from the match RNG.</li>
 *   <li>{@code period} — ticks per row shift (4..40).</li>
 *   <li>{@code length} — lane height in cells (16..32).</li>
 * </ul>
 * A fresh row is all LANE when the previous fresh row held a wall; otherwise all LANE on a coin flip, else 1..3
 * consecutive WALL cells (never more than 3 of 9, never two obstacle rows adjacent). After a shift every alive
 * seat whose pawn now sits in a WALL cell takes one damage ({@code damageSeat} honours SHIELD). Spawns: the
 * bottom lanes {@code (2 + 2*seat, length - 2)}.
 *
 * <p>Odd combinations: the shift moves cells only — BLOCK cells of a stacker and GOAL cells of a paddle drift with
 * the rows (both run as joke games; no warning exists). Sprites: tiles floor_alt (LANE) and wall. Stateless; pure.
 */
public final class Strip9Arena implements ArenaModule {

    public static final Strip9Arena INSTANCE = new Strip9Arena();

    public static final int WIDTH = 9;

    /** Most WALL cells a fresh row may hold. */
    static final int MAX_GATE = 3;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.enumeration("scroll", "none", "none", "down", "up"),
            PropertySpec.integer("period", 10, 4, 40),
            PropertySpec.integer("length", 24, 16, 32));

    private Strip9Arena() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "arena/strip_9");
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
        return props.getInt("length");
    }

    @Override
    public boolean wraps(PropertyValues props) {
        return false;
    }

    @Override
    public void build(ModuleContext ctx) {
        MatchState state = ctx.state();
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                state.setCell(x, y, Cell.LANE);
            }
        }
    }

    @Override
    public void tick(ModuleContext ctx) {
        String scroll = ctx.props().getEnum("scroll");
        if ("none".equals(scroll) || ctx.tick() % ctx.props().getInt("period") != 0) {
            return;
        }
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        boolean down = "down".equals(scroll);
        if (down) {
            for (int y = h - 1; y >= 1; y--) {
                copyRow(state, y - 1, y, w);
            }
        } else {
            for (int y = 0; y <= h - 2; y++) {
                copyRow(state, y + 1, y, w);
            }
        }
        int fresh = down ? 0 : h - 1;
        int previous = down ? 1 : h - 2;
        boolean previousHadWall = false;
        for (int x = 0; x < w; x++) {
            previousHadWall |= state.cell(x, previous) == Cell.WALL;
        }
        for (int x = 0; x < w; x++) {
            state.setCell(x, fresh, Cell.LANE);
        }
        if (!previousHadWall) {
            Rng rng = ctx.rng();
            if (!rng.chance(1, 2)) {
                int k = rng.nextInt(1, MAX_GATE);
                int start = rng.nextInt(0, w - k);
                for (int i = 0; i < k; i++) {
                    state.setCell(start + i, fresh, Cell.WALL);
                }
            }
        }
        for (int seat = 0; seat < state.seatCount(); seat++) {
            int pawn = state.pawnId(seat);
            if (!state.seatAlive(seat) || pawn < 0 || !state.exists(pawn)) {
                continue;
            }
            int cx = state.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
            int cy = state.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
            if (state.cell(cx, cy) == Cell.WALL) {
                state.damageSeat(seat, 1);
            }
        }
    }

    private static void copyRow(MatchState state, int from, int to, int w) {
        for (int x = 0; x < w; x++) {
            state.setCell(x, to, state.cell(x, from));
        }
    }

    /** The bottom lanes, two cells apart. */
    @Override
    public int[] spawnCell(ModuleContext ctx, int seat) {
        MatchState state = ctx.state();
        int x = Math.max(0, Math.min(WIDTH - 1, 2 + 2 * seat));
        return new int[] {x, state.height() - 2};
    }

    /** "9 by 24 lane · None": length and the scroll label. */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("length"),
                Component.translatable(PROPERTIES.get(0).constantLangKey(this, props.getEnum("scroll")))};
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.strip}). */
    @Override
    public String genreTag() {
        return "strip";
    }
}
