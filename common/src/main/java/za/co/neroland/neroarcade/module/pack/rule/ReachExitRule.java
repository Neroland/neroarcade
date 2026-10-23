package za.co.neroland.neroarcade.module.pack.rule;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:rule/reach_exit} — walk a pawn onto the exit cell to win. Flags {@code PLACES_EXIT, NEEDS_KEY}:
 * flags are static per chip, so NEEDS_KEY is always declared and means "may need a key"; the bench keys its
 * warnings off PLACES_EXIT only.
 *
 * <ul>
 *   <li>{@code needs_key} — the exit starts as {@code EXIT_LOCKED} and opens once no live key pickup remains.</li>
 *   <li>{@code lives} — lives per seat; when no seat is left the match is lost.</li>
 * </ul>
 *
 * <p>Placement (match start, after the arena is built, before pawns spawn): {@code s} = the arena's seat-0 spawn
 * cell. A {@code ONE_AXIS} pawn gets the exit on its own line, {@code (s.x < w/2 ? w-2 : 1, s.y)} (bench
 * {@code EXIT_ON_ONE_AXIS}); any other pawn gets the mirrored far point {@code (w-1-s.x, h-1-s.y)}. The exit is
 * the nearest EMPTY or LANE cell to that target in the default spawn ring scan (never a WALL or GOAL); a board with
 * no such cell gets no exit and can only end by lives or the hard stop. No clock.
 *
 * <p>Keys are recognised by flag only: a live PICKUP whose stack chip declares {@code IS_KEY}. With
 * {@code needs_key} and no key chip the exit opens on the first simulated tick. A seat wins when its pawn's cell is
 * EXIT; several seats arriving on the same tick win together. Stateless; pure.
 */
public final class ReachExitRule implements RuleModule {

    public static final ReachExitRule INSTANCE = new ReachExitRule();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "rule/reach_exit");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.bool("needs_key", false),
            PropertySpec.integer("lives", 3, 1, 9));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.PLACES_EXIT, ModuleFlag.NEEDS_KEY);

    private ReachExitRule() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    /** "Find exit · lives 3" / "Exit + keys · lives 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(langKey() + (props.getBool("needs_key") ? ".locked" : ".open")),
                props.getInt("lives")};
    }

    /** "Reach the exit · lives 3". */
    @Override
    public int[] hudArgs(PropertyValues props) {
        return new int[] {props.getInt("lives")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        ResolvedGame game = ctx.game();
        int lives = ctx.props().getInt("lives");
        for (int seat = 0; seat < state.seatCount(); seat++) {
            state.setLives(seat, lives);
        }
        state.setTimerTicks(0);

        int w = state.width();
        int h = state.height();
        int[] s = game.arena().spawnCell(ctx.withProps(game.arenaProps(), 0), 0);
        int tx;
        int ty;
        if (game.flags(ModuleType.PAWN).contains(ModuleFlag.ONE_AXIS)) {
            tx = s[0] < w / 2 ? w - 2 : 1;
            ty = s[1];
        } else {
            tx = w - 1 - s[0];
            ty = h - 1 - s[1];
        }
        int[] exit = nearestOpen(state, tx, ty);
        if (exit != null) {
            state.setCell(exit[0], exit[1], ctx.props().getBool("needs_key") ? Cell.EXIT_LOCKED : Cell.EXIT);
        }
    }

    @Override
    public RuleOutcome evaluate(ModuleContext ctx) {
        MatchState state = ctx.state();
        if (anyLocked(state) && !anyKeyLeft(state, ctx.game())) {
            unlockAll(state);
        }
        int mask = 0;
        for (int seat = 0; seat < state.seatCount(); seat++) {
            if (!state.seatAlive(seat)) {
                continue;
            }
            int id = state.pawnId(seat);
            if (id < 0 || !state.exists(id)) {
                continue;
            }
            int cx = state.get(id, Entities.X) >> MatchState.FP_SHIFT;
            int cy = state.get(id, Entities.Y) >> MatchState.FP_SHIFT;
            if (state.cell(cx, cy) == Cell.EXIT) {
                mask |= 1 << seat;
            }
        }
        if (mask != 0) {
            state.setWinners(mask);
            return RuleOutcome.WIN;
        }
        if (state.aliveSeatMask() == 0) {
            state.setWinners(0);
            return RuleOutcome.LOSE;
        }
        return RuleOutcome.CONTINUE;
    }

    /**
     * The nearest EMPTY or LANE in-bounds cell to {@code (tx, ty)}, scanning square rings of growing radius in the
     * same fixed order as {@code ArenaModule.spawnCell}'s default; null when the board has none.
     */
    static int[] nearestOpen(MatchState state, int tx, int ty) {
        int w = state.width();
        int h = state.height();
        int max = Math.max(w, h);
        for (int r = 0; r <= max; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = tx + dx;
                    int y = ty + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h) {
                        continue;
                    }
                    byte c = state.cell(x, y);
                    if (c == Cell.EMPTY || c == Cell.LANE) {
                        return new int[] {x, y};
                    }
                }
            }
        }
        return null;
    }

    private static boolean anyLocked(MatchState state) {
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                if (state.cell(x, y) == Cell.EXIT_LOCKED) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void unlockAll(MatchState state) {
        for (int y = 0; y < state.height(); y++) {
            for (int x = 0; x < state.width(); x++) {
                if (state.cell(x, y) == Cell.EXIT_LOCKED) {
                    state.setCell(x, y, Cell.EXIT);
                }
            }
        }
    }

    /** True while any live PICKUP's stack chip declares IS_KEY. */
    private static boolean anyKeyLeft(MatchState state, ResolvedGame game) {
        for (int i = 0; i < state.entityCount(); i++) {
            int id = state.entityIdAt(i);
            if (state.get(id, Entities.KIND) == EntityKind.PICKUP.ordinal()
                    && game.flags(ModuleType.PICKUP, state.tag(id)).contains(ModuleFlag.IS_KEY)) {
                return true;
            }
        }
        return false;
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.escape}). */
    @Override
    public String genreTag() {
        return "escape";
    }
}
