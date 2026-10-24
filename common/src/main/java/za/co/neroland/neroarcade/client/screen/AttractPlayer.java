package za.co.neroland.neroarcade.client.screen;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Replay;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * Plays a shipped attract {@link Replay} as a looping attract cycle (004 §5, 008b Q8): a {@value #TITLE_TICKS}-tick
 * title card, the replay itself one simulation step per client tick (countdown included), {@value #HOLD_TICKS}
 * ticks holding the final frame, {@value #SCORES_TICKS} ticks of high scores, then from the top on a fresh run.
 * One player is shared by every screen showing the same cartridge, so a hall of cabinets costs one simulation per
 * cartridge, not per screen.
 *
 * <p>The run re-derives nothing: it feeds the replay's input log to a fresh {@link HeadlessMatch}, which is how the
 * gallery is alive without a bot ever shipping. A replay that does not validate here (a chip this client lacks)
 * makes {@link #of} return null and the cabinet falls back to the plain attract loop. Pure: no client classes.
 */
public final class AttractPlayer {

    public enum Phase {
        TITLE,
        PLAY,
        HOLD,
        SCORES
    }

    public static final int TITLE_TICKS = 40;
    public static final int HOLD_TICKS = 40;
    public static final int SCORES_TICKS = 20;

    private final Replay replay;
    private final SeatInput[] carry = new SeatInput[Budgets.MAX_SEATS];
    private HeadlessMatch match;
    private Phase phase = Phase.TITLE;
    private int inPhase;
    private int loops;

    private AttractPlayer(Replay replay, HeadlessMatch match) {
        this.replay = replay;
        this.match = match;
        Arrays.fill(carry, SeatInput.NONE);
    }

    /** A player for {@code replay}, or null when its cartridge does not validate on this side. */
    public static @Nullable AttractPlayer of(Replay replay) {
        try {
            return new AttractPlayer(replay, fresh(replay));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static HeadlessMatch fresh(Replay replay) {
        return HeadlessMatch.of(replay.gameDef(), replay.seats(), replay.seed());
    }

    /** One client tick. */
    public void advance() {
        inPhase++;
        switch (phase) {
            case TITLE -> {
                if (inPhase >= TITLE_TICKS) {
                    next(Phase.PLAY);
                }
            }
            case PLAY -> {
                int t = match.runtime().steps();
                if (t >= replay.finalTick() || match.result().isPresent()) {
                    next(Phase.HOLD);
                    return;
                }
                match.step(replay.inputLog().inputsAt(t, carry).clone());
            }
            case HOLD -> {
                if (inPhase >= HOLD_TICKS) {
                    next(Phase.SCORES);
                }
            }
            case SCORES -> {
                if (inPhase >= SCORES_TICKS) {
                    restart();
                }
            }
        }
    }

    private void next(Phase to) {
        phase = to;
        inPhase = 0;
    }

    private void restart() {
        match = fresh(replay);
        Arrays.fill(carry, SeatInput.NONE);
        loops++;
        next(Phase.TITLE);
    }

    public Phase phase() {
        return phase;
    }

    /** Ticks spent in the current phase. */
    public int ticksInPhase() {
        return inPhase;
    }

    /** True while the screen should show the match (the run and its held last frame). */
    public boolean showsMatch() {
        return phase == Phase.PLAY || phase == Phase.HOLD;
    }

    public MatchState state() {
        return match.state();
    }

    /** Simulation steps done in this loop. */
    public int steps() {
        return match.runtime().steps();
    }

    public int loops() {
        return loops;
    }

    public Replay replay() {
        return replay;
    }

    /** Length of one full cycle in ticks. */
    public int cycleTicks() {
        return TITLE_TICKS + replay.finalTick() + 1 + HOLD_TICKS + SCORES_TICKS;
    }
}
