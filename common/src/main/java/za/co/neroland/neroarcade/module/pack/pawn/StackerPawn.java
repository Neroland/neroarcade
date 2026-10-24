package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * {@code neroarcade:pawn/stacker} — guide falling pieces; full rows clear and score. Flags {@code AUTO_MOVE},
 * {@code SCORES} (it scores on landings and row clears, so a {@code score_target} rule has a source).
 * <ul>
 *   <li>{@code fall} — auto-fall rate: {@code acc += fall * 256 / 20} per tick, one row per 256 (fall 1 ≈ a row
 *       every 22 ticks, fall 5 every 4).</li>
 *   <li>{@code shapes} — {@code bars} draws from I2 {(0,0),(1,0)} and I3 {(-1,0),(0,0),(1,0)}; {@code mixed} also
 *       from L3 {(0,0),(1,0),(0,1)} and O4 (the 2x2 square).</li>
 *   <li>{@code row_bonus} — {@code n} rows cleared by one landing score {@code row_bonus * n * n}; every landing
 *       also scores 1.</li>
 * </ul>
 * The piece is the PAWN (the pivot cell; aux: acc, counter = bit 7 hard drop / bit 6 soft drop / bits 0-5 lateral
 * hold ticks, dir = last lateral input, data bits 0-1 shape and bits 2-3 rotation) plus SEGMENTs whose aux
 * {@code data} packs their offset {@code (dx+2) | (dy+2) << 3}. {@code spawn} ignores the cell it is given: the top
 * row is the first non-WALL row at {@code x = w/2}; seat 0 spawns at the centre, 1 three cells left, 2 three right,
 * 3 six left. A spawn that overlaps anything blocking is placed anyway and costs 9 damage (top-out).
 *
 * <p>Input: left/right shifts one column per press and every 3 ticks after a 6-tick hold; A rotates clockwise about
 * the pivot (kicking one cell left, then right, else refused); B hard-drops; down held soft-drops a row per tick. On
 * landing every piece cell becomes {@code Cell.BLOCK}, the piece is removed, full rows (every cell BLOCK or WALL,
 * at least one BLOCK) among the rows it touched clear top to bottom — BLOCKs above fall one row, WALL columns stay —
 * and the next piece spawns in the same tick. Odd combinations: on a {@code shrinking_floor} VOID cells do not block,
 * so pieces fall through to the edge; on a scrolling {@code strip_9} the stack drifts with the rows; in a shared
 * {@code versus} board both seats write BLOCK and rows score to whoever lands last. Runtime opponent (override):
 * steers to the deepest column nearest its pivot (ties: lower x) and drops there; it never rotates. Sprite role
 * {@code stacker} (f0 pivot, f1 block; falls back to {@code cursor}). Stateless; pure.
 */
public final class StackerPawn implements PawnModule {

    public static final StackerPawn INSTANCE = new StackerPawn();

    static final int SHAPE_I2 = 0;
    static final int SHAPE_I3 = 1;
    static final int SHAPE_L3 = 2;
    static final int SHAPE_O4 = 3;

    /** Damage of a blocked spawn: more lives than any rule hands out. */
    static final int TOP_OUT_DAMAGE = 9;

    private static final int DROPPING = 0x80;
    private static final int SOFT = 0x40;
    private static final int HOLD_MASK = 0x3F;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("fall", 1, 1, 5),
            PropertySpec.enumeration("shapes", "mixed", "bars", "mixed"),
            PropertySpec.integer("row_bonus", 10, 1, 100));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.SCORES);

    private StackerPawn() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "pawn/stacker");
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    // ---- shapes and geometry ------------------------------------------------------------------------------

    /** The non-pivot cells of a shape as {@code dx0, dy0, dx1, dy1, …} (the pivot (0,0) is the PAWN). */
    static int[] shapeCells(int shape) {
        return switch (shape) {
            case SHAPE_I2 -> new int[] {1, 0};
            case SHAPE_I3 -> new int[] {-1, 0, 1, 0};
            case SHAPE_L3 -> new int[] {1, 0, 0, 1};
            default -> new int[] {1, 0, 0, 1, 1, 1};
        };
    }

    static int pack(int dx, int dy) {
        return (dx + 2) | ((dy + 2) << 3);
    }

    static int unpackDx(int data) {
        return (data & 7) - 2;
    }

    static int unpackDy(int data) {
        return ((data >> 3) & 7) - 2;
    }

    /** The first row from the top whose cell at {@code x = w/2} is not WALL (0 when every row is). */
    static int topRow(MatchState s) {
        int mid = s.width() / 2;
        for (int y = 0; y < s.height(); y++) {
            if (s.cell(mid, y) != Cell.WALL) {
                return y;
            }
        }
        return 0;
    }

    /** The seat's SEGMENT ids, in id order. */
    private static int[] pieceSegments(MatchState s, int seat) {
        int n = 0;
        for (int i = 0; i < s.entityCount(); i++) {
            int e = s.entityIdAt(i);
            if (s.get(e, Entities.KIND) == EntityKind.SEGMENT.ordinal() && s.get(e, Entities.OWNER) == seat) {
                n++;
            }
        }
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < s.entityCount() && k < n; i++) {
            int e = s.entityIdAt(i);
            if (s.get(e, Entities.KIND) == EntityKind.SEGMENT.ordinal() && s.get(e, Entities.OWNER) == seat) {
                out[k++] = e;
            }
        }
        return out;
    }

    /** True when the piece (pivot cell {@code (px, py)} and these offsets) would overlap anything blocking. */
    private static boolean collides(MatchState s, int px, int py, int[] dxs, int[] dys) {
        if (GridStep.blocked(s, px, py)) {
            return true;
        }
        for (int i = 0; i < dxs.length; i++) {
            if (GridStep.blocked(s, px + dxs[i], py + dys[i])) {
                return true;
            }
        }
        return false;
    }

    /** Puts the pivot at cell {@code (px, py)} and every segment at its offset from it. */
    private static void place(MatchState s, int pawn, int[] segs, int px, int py) {
        s.set(pawn, Entities.X, s.wrapX(GridStep.centre(px)));
        s.set(pawn, Entities.Y, s.wrapY(GridStep.centre(py)));
        for (int seg : segs) {
            int data = GridStep.data(s.aux(seg));
            s.set(seg, Entities.X, s.wrapX(GridStep.centre(px + unpackDx(data))));
            s.set(seg, Entities.Y, s.wrapY(GridStep.centre(py + unpackDy(data))));
        }
    }

    private static int[] offsetsX(MatchState s, int[] segs) {
        int[] out = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            out[i] = unpackDx(GridStep.data(s.aux(segs[i])));
        }
        return out;
    }

    private static int[] offsetsY(MatchState s, int[] segs) {
        int[] out = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            out[i] = unpackDy(GridStep.data(s.aux(segs[i])));
        }
        return out;
    }

    // ---- hooks --------------------------------------------------------------------------------------------

    /** Ignores {@code cellX, cellY}: pieces always enter at the top (see the class doc). */
    @Override
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState s = ctx.state();
        int w = s.width();
        int ty = topRow(s);
        int tx = w / 2 + 3 * ((seat + 1) / 2) * (seat % 2 == 0 ? 1 : -1);
        tx = Math.max(1, Math.min(w - 2, tx));
        int shape = ctx.rng().nextInt(0, "bars".equals(ctx.props().getEnum("shapes")) ? 1 : 3);
        int id = s.spawn(EntityKind.PAWN, s.wrapX(GridStep.centre(tx)), GridStep.centre(ty), seat, 0);
        s.setPawnId(seat, id);
        if (id < 0) {
            return;
        }
        s.setAux(id, GridStep.withData(s.aux(id), shape));
        int[] cells = shapeCells(shape);
        int[] dxs = new int[cells.length / 2];
        int[] dys = new int[cells.length / 2];
        for (int i = 0; i < dxs.length; i++) {
            dxs[i] = cells[2 * i];
            dys[i] = cells[2 * i + 1];
            int seg = s.spawn(EntityKind.SEGMENT, s.wrapX(GridStep.centre(tx + dxs[i])), GridStep.centre(ty + dys[i]),
                    seat, 0);
            if (seg >= 0) {
                s.setAux(seg, GridStep.withData(s.aux(seg), pack(dxs[i], dys[i])));
            }
        }
        if (collides(s, tx, ty, dxs, dys)) {
            s.damageSeat(seat, TOP_OUT_DAMAGE);
        }
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int flags = GridStep.counter(aux);
        int hold = flags & HOLD_MASK;
        boolean dropping = (flags & DROPPING) != 0;
        int m = intent.moveX();
        int code = m > 0 ? GridStep.RIGHT : m < 0 ? GridStep.LEFT : GridStep.UP;
        int[] segs = pieceSegments(s, seat);
        if (m == 0) {
            hold = 0;
        } else {
            if (code != GridStep.dir(aux)) {
                hold = 0;
            }
            hold++;
            if (hold > HOLD_MASK) {
                hold = HOLD_MASK - 2;
            }
            if (hold == 1 || (hold > 6 && (hold - 6) % 3 == 0)) {
                int px = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
                int py = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
                if (!collides(s, px + m, py, offsetsX(s, segs), offsetsY(s, segs))) {
                    place(s, id, segs, px + m, py);
                }
            }
        }
        if (intent.aPressed()) {
            rotate(s, id, segs);
        }
        if (intent.bPressed()) {
            dropping = true;
        }
        int counter = hold | (dropping ? DROPPING : 0) | (intent.moveY() > 0 ? SOFT : 0);
        aux = s.aux(id);
        aux = GridStep.withDir(aux, code);
        aux = GridStep.withCounter(aux, counter);
        s.setAux(id, aux);
    }

    /** Clockwise {@code (dx, dy) -> (-dy, dx)} about the pivot; kicks x-1 then x+1; refused when all three block. */
    private static void rotate(MatchState s, int pawn, int[] segs) {
        int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
        int py = s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
        int[] dxs = new int[segs.length];
        int[] dys = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            int data = GridStep.data(s.aux(segs[i]));
            dxs[i] = -unpackDy(data);
            dys[i] = unpackDx(data);
        }
        int nx;
        if (!collides(s, px, py, dxs, dys)) {
            nx = px;
        } else if (!collides(s, px - 1, py, dxs, dys)) {
            nx = px - 1;
        } else if (!collides(s, px + 1, py, dxs, dys)) {
            nx = px + 1;
        } else {
            return;
        }
        for (int i = 0; i < segs.length; i++) {
            s.setAux(segs[i], GridStep.withData(s.aux(segs[i]), pack(dxs[i], dys[i])));
        }
        int aux = s.aux(pawn);
        int data = GridStep.data(aux);
        int rotation = ((data >> 2) + 1) & 3;
        s.setAux(pawn, GridStep.withData(aux, (data & 3) | (rotation << 2)));
        place(s, pawn, segs, nx, py);
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int flags = GridStep.counter(aux);
        boolean dropping = (flags & DROPPING) != 0;
        boolean soft = (flags & SOFT) != 0;
        int acc = GridStep.acc(aux) + (ctx.props().getInt("fall") * MatchState.FP) / 20;
        boolean step = dropping || soft || acc >= MatchState.FP;
        if (acc >= MatchState.FP) {
            acc -= MatchState.FP;
        }
        s.setAux(id, GridStep.withAcc(aux, acc));
        if (step) {
            int[] segs = pieceSegments(s, seat);
            int[] dxs = offsetsX(s, segs);
            int[] dys = offsetsY(s, segs);
            // a hard drop falls at most one full board height: on a board that wraps top to bottom an empty column
            // has no floor, and an unbounded drop never ends (CombinationFuzzTest, final review H1) — a piece that
            // has gone a whole lap without touching anything locks where it is
            int fallen = 0;
            while (true) {
                int px = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
                int py = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
                if (collides(s, px, py + 1, dxs, dys) || ++fallen > s.height()) {
                    land(ctx, seat, id, segs, px, py, dxs, dys);
                    break;
                }
                place(s, id, segs, px, py + 1);
                if (!dropping) {
                    break;
                }
            }
        }
        GridStep.moveProjectiles(s, seat);
    }

    /** Writes BLOCK, removes the piece, clears full rows it touched (top to bottom), scores, spawns the next. */
    private void land(ModuleContext ctx, int seat, int pawn, int[] segs, int px, int py, int[] dxs, int[] dys) {
        MatchState s = ctx.state();
        int n = dxs.length + 1;
        int[] rows = new int[n];
        rows[0] = py;
        s.setCell(px, py, Cell.BLOCK);
        for (int i = 0; i < dxs.length; i++) {
            s.setCell(px + dxs[i], py + dys[i], Cell.BLOCK);
            rows[i + 1] = py + dys[i];
        }
        java.util.Arrays.sort(rows);
        s.removeSeatBody(seat);
        int cleared = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0 && rows[i] == rows[i - 1]) {
                continue;
            }
            int row = GridStep.wrapCellY(s, rows[i]);
            if (row >= 0 && row < s.height() && fullRow(s, row)) {
                clearRow(s, row);
                cleared++;
            }
        }
        s.addScore(seat, 1);
        if (cleared > 0) {
            s.addScore(seat, ctx.props().getInt("row_bonus") * cleared * cleared);
        }
        spawn(ctx, seat, 0, 0);
    }

    /** Every cell BLOCK or WALL and at least one BLOCK (a border row of WALL never clears). */
    static boolean fullRow(MatchState s, int y) {
        boolean any = false;
        for (int x = 0; x < s.width(); x++) {
            byte c = s.cell(x, y);
            if (c != Cell.BLOCK && c != Cell.WALL) {
                return false;
            }
            any |= c == Cell.BLOCK;
        }
        return any;
    }

    /** Rows above {@code row} drop one: BLOCKs fall, WALL columns and other cells stay; row 0's BLOCKs empty. */
    static void clearRow(MatchState s, int row) {
        int w = s.width();
        for (int y = row; y >= 1; y--) {
            for (int x = 0; x < w; x++) {
                byte here = s.cell(x, y);
                if (here == Cell.WALL) {
                    continue;
                }
                byte above = s.cell(x, y - 1);
                s.setCell(x, y, above == Cell.BLOCK ? Cell.BLOCK : (here == Cell.BLOCK ? Cell.EMPTY : here));
            }
        }
        for (int x = 0; x < w; x++) {
            if (s.cell(x, 0) == Cell.BLOCK) {
                s.setCell(x, 0, Cell.EMPTY);
            }
        }
    }

    /**
     * Steers the pivot to the deepest column (the first BLOCK/WALL from the top row down lies lowest) nearest the
     * pivot, ties to the lower x, then drops: B (hard drop on its edge) plus down (soft drop, so a B held across two
     * decisions still descends). Never rotates. Pure.
     */
    @Override
    public SeatInput opponentInput(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int pawn = s.pawnId(seat);
        if (pawn < 0 || !s.exists(pawn)) {
            return SeatInput.NONE;
        }
        int w = s.width();
        int h = s.height();
        int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
        int lo = s.cell(0, h / 2) == Cell.WALL ? 1 : 0;
        int hi = s.cell(w - 1, h / 2) == Cell.WALL ? w - 2 : w - 1;
        int top = topRow(s);
        int best = px;
        int bestDepth = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int x = lo; x <= hi; x++) {
            int depth = h;
            for (int y = top; y < h; y++) {
                byte c = s.cell(x, y);
                if (c == Cell.BLOCK || c == Cell.WALL) {
                    depth = y;
                    break;
                }
            }
            int dist = Math.abs(x - px);
            if (depth > bestDepth || (depth == bestDepth && dist < bestDist)) {
                best = x;
                bestDepth = depth;
                bestDist = dist;
            }
        }
        if (best < px) {
            return SeatInput.of(SeatInput.LEFT, 0, 0);
        }
        if (best > px) {
            return SeatInput.of(SeatInput.RIGHT, 0, 0);
        }
        return SeatInput.of(SeatInput.B | SeatInput.DOWN, 0, 0);
    }

    /** "Fall 1 · bonus 10". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("fall"), props.getInt("row_bonus")};
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.stack}). */
    @Override
    public String genreTag() {
        return "stack";
    }
}
