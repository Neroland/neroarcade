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
 * {@code neroarcade:control/twin_stick} — move with the pad, aim with the stick, fire with A. Flags {@code NEEDS_AIM}.
 * Stateless; pure.
 *
 * <p>Movement is four-way with diagonals always on ({@code moveX = RIGHT − LEFT}, {@code moveY = DOWN − UP}); A/B
 * levels and edges as {@link FourWayControl}. Aim comes from {@code SeatInput.aimX/aimY} (down is +). Property
 * {@code aim} ({@code eight_way} | {@code free}, default eight_way): {@code free} passes the stick through clamped to
 * ±127; {@code eight_way} quantises each axis to {−127, 0, 127} with a dead zone of 32 ({@code |v| < 32 → 0}).
 *
 * <p>Odd combinations: on a pawn without {@code FIRES} the bench shows {@code AIM_WITHOUT_FIRING_PAWN} and the aim is
 * simply ignored — the chip then plays as an eight-direction pad.
 */
public final class TwinStickControl implements ControlModule {

    public static final TwinStickControl INSTANCE = new TwinStickControl();

    public static final String EIGHT_WAY = "eight_way";
    public static final String FREE = "free";

    /** Below this magnitude an eight-way aim axis reads 0. */
    public static final int DEAD_ZONE = 32;

    /** Full stick deflection. */
    public static final int FULL = 127;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "control/twin_stick");

    private static final List<PropertySpec> PROPERTIES =
            List.of(PropertySpec.enumeration("aim", EIGHT_WAY, EIGHT_WAY, FREE));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.NEEDS_AIM);

    private TwinStickControl() {
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
        int mx = (now.has(SeatInput.RIGHT) ? 1 : 0) - (now.has(SeatInput.LEFT) ? 1 : 0);
        int my = (now.has(SeatInput.DOWN) ? 1 : 0) - (now.has(SeatInput.UP) ? 1 : 0);
        boolean free = FREE.equals(ctx.props().getEnum("aim"));
        int ax = free ? clamp(now.aimX()) : quantise(now.aimX());
        int ay = free ? clamp(now.aimY()) : quantise(now.aimY());
        boolean a = now.has(SeatInput.A);
        boolean b = now.has(SeatInput.B);
        return new Intent(mx, my, ax, ay, a, b, a && !previous.has(SeatInput.A), b && !previous.has(SeatInput.B));
    }

    /** Clamps to ±127. */
    static int clamp(int v) {
        return Math.max(-FULL, Math.min(FULL, v));
    }

    /** {@code |v| < 32 → 0}, otherwise ±127 by sign. */
    static int quantise(int v) {
        if (v > -DEAD_ZONE && v < DEAD_ZONE) {
            return 0;
        }
        return v > 0 ? FULL : -FULL;
    }

    /** Two lines: eight-way movement, then the aim keys (the keycap line already names the buttons). */
    @Override
    public List<String> legend(PropertyValues props) {
        return List.of("neroarcade.legend.move_8way", "neroarcade.legend.aim_stick");
    }

    /** "Move · 8-way aim · A" / "Move · free aim · A". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(langKey() + (FREE.equals(props.getEnum("aim")) ? ".aim_free"
                : ".aim_8"))};
    }
}
