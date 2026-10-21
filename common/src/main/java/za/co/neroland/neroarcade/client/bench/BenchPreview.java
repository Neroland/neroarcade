package za.co.neroland.neroarcade.client.bench;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.screen.SpriteMap;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.runtime.ArcadeRuntime;
import za.co.neroland.neroarcade.runtime.MatchPhase;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The bench's live preview: the current draft run headless on the client through the real runtime, with a fixed
 * seed and a scripted input (the pawn walks a square), restarted on every draft change and two seconds after
 * the demo match ends. The countdown is skipped. It only ever reads its own state; nothing here is sent.
 *
 * <p><b>Cost cap:</b> at most {@link #MAX_STEPS_PER_CALL} runtime steps per {@link #advance} call (the screen calls
 * it once per client tick with 1, so 20 steps a second), and none while the game window is not focused.
 * Pure.
 */
public final class BenchPreview {

    public static final long SEED = 0x5EEDL;
    public static final int MAX_STEPS_PER_CALL = 20;
    /** Ticks the ended demo stays on screen before it restarts. */
    public static final int HOLD_TICKS = 40;
    /** One lap of the scripted square: 16 ticks each right, down, left, up. */
    public static final int LAP = 64;

    private @Nullable ResolvedGame game;
    private @Nullable ArcadeRuntime runtime;
    private SpriteMap map = SpriteMap.DEFAULT;
    private int held;
    private int restarts;
    private SeatInput[] inputs = new SeatInput[1];

    /** Shows {@code next}; restarts when it is a different game (null = nothing to preview). */
    public void setGame(@Nullable ResolvedGame next) {
        if (next == game) {
            return;
        }
        game = next;
        map = SpriteMap.of(next);
        restart();
    }

    public void restart() {
        held = 0;
        ResolvedGame current = game;
        if (current == null) {
            runtime = null;
            return;
        }
        try {
            ArcadeRuntime fresh = new ArcadeRuntime(current, ArcadeRuntime.newState(current, current.seats()), null);
            fresh.start(SEED);
            int guard = 0;
            while (fresh.state().phase() == MatchPhase.COUNTDOWN && guard++ < 200) {
                fresh.step(inputsFor(fresh.state(), 0));
            }
            runtime = fresh;
            restarts++;
        } catch (RuntimeException e) {
            runtime = null;
        }
    }

    /**
     * Advances the demo by up to {@code ticks} steps (capped at {@link #MAX_STEPS_PER_CALL}).
     *
     * @return the steps actually run
     */
    public int advance(int ticks) {
        ArcadeRuntime current = runtime;
        if (current == null) {
            return 0;
        }
        int steps = Math.max(0, Math.min(MAX_STEPS_PER_CALL, ticks));
        int ran = 0;
        for (int i = 0; i < steps; i++) {
            if (current.state().phase() == MatchPhase.ENDED) {
                if (++held >= HOLD_TICKS) {
                    restart();
                    return ran;
                }
                continue;
            }
            try {
                current.step(inputsFor(current.state(), current.state().tick()));
            } catch (RuntimeException e) {
                runtime = null; // a broken chip combination simply stops the demo
                return ran;
            }
            ran++;
        }
        return ran;
    }

    private SeatInput[] inputsFor(MatchState state, int tick) {
        int seats = Math.max(1, state.seatCount());
        if (inputs.length != seats) {
            inputs = new SeatInput[seats];
        }
        java.util.Arrays.fill(inputs, SeatInput.NONE);
        inputs[0] = scripted(tick);
        return inputs;
    }

    /** The demo's input: a square walk, 16 ticks per side. Pure. */
    public static SeatInput scripted(int tick) {
        int side = Math.floorMod(tick, LAP) / (LAP / 4);
        int button = switch (side) {
            case 0 -> SeatInput.RIGHT;
            case 1 -> SeatInput.DOWN;
            case 2 -> SeatInput.LEFT;
            default -> SeatInput.UP;
        };
        return SeatInput.of(button, 0, 0);
    }

    public @Nullable MatchState state() {
        ArcadeRuntime current = runtime;
        return current == null ? null : current.state();
    }

    public SpriteMap spriteMap() {
        return map;
    }

    /** Times the demo has (re)started (tests and the cost readout). */
    public int restarts() {
        return restarts;
    }
}
