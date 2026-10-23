package za.co.neroland.neroarcade.module.pack.modifier;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:modifier/wrap_around} — the board's edges join up. No flags.
 *
 * <ul>
 *   <li>{@code axes} — {@code both}, {@code horizontal} (left/right join) or {@code vertical} (top/bottom join).</li>
 * </ul>
 *
 * <p>At match start (after the arena is built) wrapping is OR-ed into the arena's own topology and, for each axis
 * that was not already wrapping, the border is opened: every WALL on that axis' two edge lines (x = 0 and x = w-1
 * for horizontal, y = 0 and y = h-1 for vertical, corners included) becomes EMPTY. Interior walls (pillars, mazes)
 * are untouched. Odd combinations: an arena that already wraps on an axis is left unchanged there (no warning);
 * a paddle's goal lines are not WALL so they stay, and a ball wrapping past them makes a goal-less duel (runs).
 * Stateless; pure.
 */
public final class WrapAroundModifier implements ModifierModule {

    public static final WrapAroundModifier INSTANCE = new WrapAroundModifier();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "modifier/wrap_around");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.enumeration("axes", "both", "both", "horizontal", "vertical"));

    private WrapAroundModifier() {
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
        return Set.of();
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        String axes = ctx.props().getEnum("axes");
        boolean hx = !axes.equals("vertical");
        boolean vy = !axes.equals("horizontal");
        boolean openX = hx && !state.wrapsX();
        boolean openY = vy && !state.wrapsY();
        state.setWrap(state.wrapsX() || hx, state.wrapsY() || vy);
        int w = state.width();
        int h = state.height();
        if (openX) {
            for (int y = 0; y < h; y++) {
                open(state, 0, y);
                open(state, w - 1, y);
            }
        }
        if (openY) {
            for (int x = 0; x < w; x++) {
                open(state, x, 0);
                open(state, x, h - 1);
            }
        }
    }

    private static void open(MatchState state, int x, int y) {
        if (state.cell(x, y) == Cell.WALL) {
            state.setCell(x, y, Cell.EMPTY);
        }
    }

    /** "Edges wrap: Both". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(PROPERTIES.get(0).constantLangKey(this, props.getEnum("axes")))};
    }
}
