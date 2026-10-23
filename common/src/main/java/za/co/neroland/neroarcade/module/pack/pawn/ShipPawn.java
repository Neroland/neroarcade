package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:pawn/ship} — drifts with inertia and fires shots where it points. Flag {@code FIRES}.
 * <ul>
 *   <li>{@code thrust} — each tick of input adds {@code thrust * 8} units/tick to that axis' velocity (SPEED adds 2
 *       to thrust, STUN removes it); each axis is clamped to ±320.</li>
 *   <li>{@code drag} — {@code none} (it coasts forever), {@code light} ({@code |v| -= max(1, |v| >> 4)}) or
 *       {@code heavy} ({@code |v| -= max(1, |v| >> 3)}) per tick, toward zero.</li>
 *   <li>{@code fires} — A fires a PROJECTILE (owner = seat, TTL 40, at most one cell per tick per axis, so it never
 *       tunnels) along the aim stick when it is off-centre, else along the facing; 10-tick cooldown. With
 *       {@code fires = false} aim is ignored (the FIRES flag is static, so the bench cannot warn).</li>
 * </ul>
 * Facing = the last non-zero move (4-way, X first), starting up. An axis that hits a blocking cell or a solid
 * entity loses its velocity. Odd combinations: with a {@code BUTTONS_ONLY} control it thrusts along its facing,
 * A reverses the facing (and fires when {@code fires}), B held brakes to a stop; with {@code four_way} it fires
 * along its facing. Runtime opponent: the stock greedy one (FIRES makes it hunt and press A when aligned). Sprite
 * role {@code ship}; shots are the renderer's flash square. Stateless; pure.
 */
public final class ShipPawn implements PawnModule {

    public static final ShipPawn INSTANCE = new ShipPawn();

    /** Largest velocity on either axis (1.25 cells per tick). */
    static final int MAX_VELOCITY = 320;
    /** Shot lifetime in ticks. */
    static final int SHOT_TTL = 40;
    /** Ticks between shots. */
    static final int COOLDOWN = 10;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("thrust", 3, 1, 5),
            PropertySpec.enumeration("drag", "light", "none", "light", "heavy"),
            PropertySpec.bool("fires", true));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.FIRES);

    private ShipPawn() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "pawn/ship");
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
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState s = ctx.state();
        int id = s.spawn(EntityKind.PAWN, GridStep.centre(cellX), GridStep.centre(cellY), seat, 0);
        s.setPawnId(seat, id);
        // facing 0 = up and cooldown 0: the fresh aux word already says so
    }

    /** {@code v} moved toward zero by {@code max(1, |v| >> shift)}; {@code shift < 0} = no drag. */
    static int drag(int v, int shift) {
        if (shift < 0 || v == 0) {
            return v;
        }
        int mag = Math.abs(v);
        mag = Math.max(0, mag - Math.max(1, mag >> shift));
        return Integer.signum(v) * mag;
    }

    private static int dragShift(String drag) {
        return switch (drag) {
            case "light" -> 4;
            case "heavy" -> 3;
            default -> -1;
        };
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int dir = GridStep.dir(aux);
        int vx = s.get(id, Entities.VX);
        int vy = s.get(id, Entities.VY);
        int thrust = GridStep.speed(s, seat, ctx.props().getInt("thrust")) * 8;
        boolean fire;
        if (GridStep.buttonsOnly(ctx)) {
            if (intent.aPressed()) {
                dir = GridStep.reverse(dir);
            }
            if (intent.b()) {
                vx = 0;
                vy = 0;
            } else {
                vx += GridStep.dx(dir) * thrust;
                vy += GridStep.dy(dir) * thrust;
            }
            fire = intent.aPressed();
        } else {
            vx += intent.moveX() * thrust;
            vy += intent.moveY() * thrust;
            if (intent.moveX() != 0) {
                dir = intent.moveX() > 0 ? GridStep.RIGHT : GridStep.LEFT;
            } else if (intent.moveY() != 0) {
                dir = intent.moveY() > 0 ? GridStep.DOWN : GridStep.UP;
            }
            fire = intent.aPressed() || intent.a();
        }
        int shift = dragShift(ctx.props().getEnum("drag"));
        vx = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, drag(vx, shift)));
        vy = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, drag(vy, shift)));
        s.set(id, Entities.VX, vx);
        s.set(id, Entities.VY, vy);
        int cooldown = GridStep.counter(aux);
        if (fire && cooldown == 0 && ctx.props().getBool("fires")) {
            int ax = intent.aimX();
            int ay = intent.aimY();
            if (ax == 0 && ay == 0) {
                ax = GridStep.dx(dir);
                ay = GridStep.dy(dir);
            }
            int m = Math.max(Math.abs(ax), Math.abs(ay));
            int shot = s.spawn(EntityKind.PROJECTILE,
                    GridStep.centre(s.get(id, Entities.X) >> MatchState.FP_SHIFT),
                    GridStep.centre(s.get(id, Entities.Y) >> MatchState.FP_SHIFT), seat, 0);
            if (shot >= 0) {
                s.set(shot, Entities.VX, ax * MatchState.FP / m);
                s.set(shot, Entities.VY, ay * MatchState.FP / m);
                s.set(shot, Entities.TTL, SHOT_TTL);
                cooldown = COOLDOWN;
            }
        }
        aux = GridStep.withDir(aux, dir);
        aux = GridStep.withCounter(aux, cooldown);
        s.setAux(id, aux);
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int x0 = s.get(id, Entities.X);
        int y0 = s.get(id, Entities.Y);
        int vx = s.get(id, Entities.VX);
        int vy = s.get(id, Entities.VY);
        int x = GridStep.move(s, x0, y0, vx, true);
        if (x != s.wrapX(x0 + vx)) {
            s.set(id, Entities.VX, 0);
        }
        int y = GridStep.move(s, x, y0, vy, false);
        if (y != s.wrapY(y0 + vy)) {
            s.set(id, Entities.VY, 0);
        }
        s.set(id, Entities.X, x);
        s.set(id, Entities.Y, y);
        int aux = s.aux(id);
        int cooldown = GridStep.counter(aux);
        if (cooldown > 0) {
            s.setAux(id, GridStep.withCounter(aux, cooldown - 1));
        }
        GridStep.moveProjectiles(s, seat);
        GridStep.voidCheck(s, seat);
    }

    /** "Thrust 3 · drag Light". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("thrust"),
                Component.translatable(PROPERTIES.get(1).constantLangKey(this, props.getEnum("drag")))};
    }
}
