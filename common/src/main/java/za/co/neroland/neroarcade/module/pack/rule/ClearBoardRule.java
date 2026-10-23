package za.co.neroland.neroarcade.module.pack.rule;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:rule/clear_board} — collect every clearable pickup before the clock runs out. Flags
 * {@code NEEDS_CLEARABLE}.
 *
 * <ul>
 *   <li>{@code lives} — lives per seat; when no seat is left the match is lost.</li>
 *   <li>{@code seconds} — the time limit (timer = seconds × 20 simulated ticks); at zero the match is lost.</li>
 * </ul>
 *
 * <p>The board is "clear" when no live PICKUP remains whose stack chip declares {@code CLEARABLE}
 * ({@code game.flags(PICKUP, state.tag(id))}); the seats still alive then win. A CLEARABLE chip sees this rule's
 * {@code NEEDS_CLEARABLE} and places its pickups once at match start, never respawning them (001 A8).
 *
 * <p>Odd combinations: with no CLEARABLE pickup chip at all the rule plays as {@code survive_time} of
 * {@code seconds} (timer zero is then a WIN; bench {@code CLEAR_BOARD_WITHOUT_PICKUPS}). A8's "at least one was ever
 * placed" is checked statelessly: clearable pickups are placed by the pickups' {@code onMatchStart}, so the first
 * simulated tick (tick 0) never counts as cleared; a clearable chip whose spawns all failed against a full entity
 * budget therefore wins on tick 1 (documented edge case). Should the last pickup go on the same tick the last seat
 * dies, the loss wins (no WIN with an empty winners mask). Stateless; pure.
 */
public final class ClearBoardRule implements RuleModule {

    public static final ClearBoardRule INSTANCE = new ClearBoardRule();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "rule/clear_board");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("lives", 3, 1, 9),
            PropertySpec.integer("seconds", 120, 10, 600));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.NEEDS_CLEARABLE);

    private ClearBoardRule() {
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

    /** "Clear 120 s · lives 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("seconds"), props.getInt("lives")};
    }

    /** "Clear in 120 s · lives 3". */
    @Override
    public int[] hudArgs(PropertyValues props) {
        return new int[] {props.getInt("seconds"), props.getInt("lives")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        state.setTimerTicks(ctx.props().getInt("seconds") * Budgets.TICKS_PER_SECOND);
        int lives = ctx.props().getInt("lives");
        for (int seat = 0; seat < state.seatCount(); seat++) {
            state.setLives(seat, lives);
        }
    }

    @Override
    public RuleOutcome evaluate(ModuleContext ctx) {
        MatchState state = ctx.state();
        ResolvedGame game = ctx.game();
        if (state.aliveSeatMask() == 0) {
            state.setWinners(0);
            return RuleOutcome.LOSE;
        }
        if (!hasClearableChip(game)) {
            // fallback: survive_time of `seconds`
            if (state.timerTicks() == 0) {
                state.setWinners(state.aliveSeatMask());
                return RuleOutcome.WIN;
            }
            return RuleOutcome.CONTINUE;
        }
        if (state.tick() > 0 && liveClearables(state, game) == 0) {
            state.setWinners(state.aliveSeatMask());
            return RuleOutcome.WIN;
        }
        if (state.timerTicks() == 0) {
            state.setWinners(0);
            return RuleOutcome.LOSE;
        }
        return RuleOutcome.CONTINUE;
    }

    /** True when any chip in the PICKUP stack declares CLEARABLE. */
    static boolean hasClearableChip(ResolvedGame game) {
        for (int i = 0; i < game.pickups().size(); i++) {
            if (game.flags(ModuleType.PICKUP, i).contains(ModuleFlag.CLEARABLE)) {
                return true;
            }
        }
        return false;
    }

    /** Live PICKUP entities whose stack chip declares CLEARABLE. */
    static int liveClearables(MatchState state, ResolvedGame game) {
        int count = 0;
        for (int i = 0; i < state.entityCount(); i++) {
            int id = state.entityIdAt(i);
            if (state.get(id, Entities.KIND) == EntityKind.PICKUP.ordinal()
                    && game.flags(ModuleType.PICKUP, state.tag(id)).contains(ModuleFlag.CLEARABLE)) {
                count++;
            }
        }
        return count;
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.score}). */
    @Override
    public String genreTag() {
        return "score";
    }
}
