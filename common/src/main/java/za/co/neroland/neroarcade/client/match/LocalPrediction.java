package za.co.neroland.neroarcade.client.match;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.runtime.ArcadeRuntime;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchPhase;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * Local-pawn prediction for the seated player only (002 §8.3): a <b>shadow copy</b> of the latest authoritative
 * state run through the real {@link ArcadeRuntime} with this seat's own inputs, at most {@link #HORIZON} ticks
 * ahead of the latest snapshot. Only the seat's own pawn (and serpent segments) are drawn from the shadow; every
 * other entity, every score, the timer and the phase come from the authoritative state, so RNG-driven effects in
 * the shadow are simply ignored. The client never decides anything from it: nothing here is sent.
 *
 * <p><b>Reconcile.</b> The shadow remembers where it put the pawn at each predicted tick. When a snapshot for one of
 * those ticks arrives, the prediction is compared with the truth: more than one cell apart on either axis
 * ({@link #SNAP_UNITS}) is a misprediction, so the pawn <b>snaps</b> — the shadow restarts from the snapshot with the
 * input ring cleared. Otherwise the shadow is rebuilt from the snapshot and the last ({@link #HORIZON}-entry ring)
 * inputs re-applied, so the pawn keeps moving smoothly ahead of the server.
 */
public final class LocalPrediction {

    /** Most ticks the shadow may run ahead of the latest snapshot. */
    public static final int HORIZON = 3;
    /** Error (fixed point, either axis) above which the pawn snaps to the authoritative position: one cell. */
    public static final int SNAP_UNITS = MatchState.FP;

    private final ResolvedGame game;
    private final int seat;
    private final SeatInput[] ring = new SeatInput[HORIZON];
    private int ringSize;
    private int ringHead;
    /** Predicted pawn position per shadow tick, for reconciliation: {tick, x, y} x (HORIZON + 1). */
    private final int[] history = new int[(HORIZON + 1) * 3];
    private int historyHead;
    private @Nullable MatchState authoritative;
    private @Nullable MatchState shadow;
    private @Nullable ArcadeRuntime runtime;
    private SeatInput[] inputs = new SeatInput[0];
    private int ahead;
    private int snaps;

    public LocalPrediction(ResolvedGame game, int seat) {
        this.game = game;
        this.seat = seat;
        Arrays.fill(history, Integer.MIN_VALUE);
    }

    public int seat() {
        return seat;
    }

    /** Ticks the shadow is ahead of the latest snapshot (0..{@link #HORIZON}). */
    public int ahead() {
        return ahead;
    }

    /** How often the pawn snapped back to the authoritative position. */
    public int snaps() {
        return snaps;
    }

    /** True when a predicted pawn more than one cell from the authoritative pawn must snap. Pure. */
    public static boolean shouldSnap(int predictedX, int predictedY, int trueX, int trueY) {
        return Math.abs(predictedX - trueX) > SNAP_UNITS || Math.abs(predictedY - trueY) > SNAP_UNITS;
    }

    /** A new authoritative state: reconcile, rebuild the shadow, re-apply the remembered inputs (≤ the horizon). */
    public void onSnapshot(MatchState state) {
        authoritative = state;
        if (mispredicted(state)) {
            snaps++;
            ringSize = 0; // snap: the pawn is drawn exactly where the server has it
        }
        rebuild();
        int start = Math.floorMod(ringHead - ringSize, HORIZON);
        for (int i = 0; i < ringSize; i++) {
            advance(ring[(start + i) % HORIZON]);
        }
    }

    /**
     * One client tick with this seat's current input: remembered in the ring and, while the shadow is less than
     * {@link #HORIZON} ticks ahead and the match is PLAYING, applied to it.
     *
     * @return true if the shadow advanced
     */
    public boolean tick(SeatInput input) {
        SeatInput in = input == null ? SeatInput.NONE : input;
        ring[ringHead] = in;
        ringHead = (ringHead + 1) % HORIZON;
        ringSize = Math.min(HORIZON, ringSize + 1);
        return advance(in);
    }

    private boolean mispredicted(MatchState truth) {
        int pawn = truth.pawnId(seat);
        if (pawn < 0 || !truth.exists(pawn)) {
            return false;
        }
        for (int i = 0; i <= HORIZON; i++) {
            int base = i * 3;
            if (history[base] == truth.tick()) {
                return shouldSnap(history[base + 1], history[base + 2], truth.get(pawn, Entities.X),
                        truth.get(pawn, Entities.Y));
            }
        }
        return false;
    }

    private boolean advance(SeatInput input) {
        MatchState predicted = shadow;
        if (runtime == null || predicted == null || ahead >= HORIZON || predicted.phase() != MatchPhase.PLAYING) {
            return false;
        }
        int seats = predicted.seatCount();
        if (inputs.length != seats) {
            inputs = new SeatInput[seats];
        }
        Arrays.fill(inputs, SeatInput.NONE);
        if (seat >= 0 && seat < seats) {
            inputs[seat] = input;
        }
        runtime.step(inputs);
        ahead++;
        remember(predicted);
        return true;
    }

    private void remember(MatchState predicted) {
        int pawn = predicted.pawnId(seat);
        if (pawn < 0 || !predicted.exists(pawn)) {
            return;
        }
        int base = historyHead * 3;
        history[base] = predicted.tick();
        history[base + 1] = predicted.get(pawn, Entities.X);
        history[base + 2] = predicted.get(pawn, Entities.Y);
        historyHead = (historyHead + 1) % (HORIZON + 1);
    }

    private void rebuild() {
        MatchState base = authoritative;
        ahead = 0;
        if (base == null) {
            shadow = null;
            runtime = null;
            return;
        }
        MatchState copy = base.copy();
        shadow = copy;
        runtime = new ArcadeRuntime(game, copy, null);
    }

    /** True when {@code id} is drawn from the shadow: this seat's pawn or segment, present in the shadow. */
    public boolean owns(MatchState state, int id) {
        MatchState predicted = shadow;
        if (predicted == null || seat < 0 || !predicted.exists(id) || !state.exists(id)) {
            return false;
        }
        int kind = state.get(id, Entities.KIND);
        return (kind == EntityKind.PAWN.ordinal() || kind == EntityKind.SEGMENT.ordinal())
                && state.get(id, Entities.OWNER) == seat;
    }

    /** The predicted fixed-point x of {@code id}; call {@link #owns} first. */
    public int x(int id) {
        MatchState predicted = shadow;
        return predicted == null ? 0 : predicted.get(id, Entities.X);
    }

    public int y(int id) {
        MatchState predicted = shadow;
        return predicted == null ? 0 : predicted.get(id, Entities.Y);
    }

    /** The shadow state (tests). */
    public @Nullable MatchState shadow() {
        return shadow;
    }
}
