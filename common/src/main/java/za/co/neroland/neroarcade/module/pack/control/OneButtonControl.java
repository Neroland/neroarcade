package za.co.neroland.neroarcade.module.pack.control;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * {@code neroarcade:control/one_button} — one action button and one alternate; no steering. Flags {@code ONE_AXIS},
 * {@code BUTTONS_ONLY}. Stateless; pure.
 *
 * <p>Movement is always 0 on both axes and aim is 0. Edges: {@code aPressed = A && !prevA}, {@code bPressed} likewise.
 * Property {@code hold} (default false): false makes the level equal the edge ({@code a = aPressed}), so holding A
 * does nothing more than tapping it; true reports the held level ({@code a = A}).
 *
 * <p>Odd combinations: on a pawn without {@code AUTO_MOVE} the bench shows {@code ONE_BUTTON_FREE_PAWN}, and the pawn
 * auto-moves in its facing at its speed with A reversing and B (held) stopping it (001 §6 row 5, 008 BUTTONS_ONLY
 * default — the pawn chip implements it by reading this flag). {@code BUTTONS_ONLY} suppresses
 * {@code TWO_WAY_ON_FREE_PAWN} despite {@code ONE_AXIS}.
 */
public final class OneButtonControl implements ControlModule {

    public static final OneButtonControl INSTANCE = new OneButtonControl();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "control/one_button");

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.bool("hold", false));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.ONE_AXIS, ModuleFlag.BUTTONS_ONLY);

    private OneButtonControl() {
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

    @Override
    public Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous) {
        boolean heldA = now.has(SeatInput.A);
        boolean heldB = now.has(SeatInput.B);
        boolean aPressed = heldA && !previous.has(SeatInput.A);
        boolean bPressed = heldB && !previous.has(SeatInput.B);
        boolean hold = ctx.props().getBool("hold");
        return new Intent(0, 0, 0, 0, hold ? heldA : aPressed, hold ? heldB : bPressed, aPressed, bPressed);
    }

    /** Exactly one line: there is no steering (the keycap line already names the two buttons). */
    @Override
    public List<String> legend(PropertyValues props) {
        return List.of("neroarcade.legend.one_button");
    }

    /** "Buttons only · tap" / "Buttons only · hold". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(langKey() + (props.getBool("hold") ? ".held" : ".tap"))};
    }
}
