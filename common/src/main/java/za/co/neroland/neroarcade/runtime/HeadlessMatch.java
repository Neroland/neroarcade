package za.co.neroland.neroarcade.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.runtime.net.Snapshot;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

/**
 * A match with no server: tests, the gallery's attract replays and the bench preview drive the real
 * {@link ArcadeRuntime} through this harness. Every step is logged to an {@link InputLog}, so any run can be
 * turned into a {@link Replay}.
 *
 * <p><b>Invariant:</b> nothing here touches a level, a player, a clock or a non-match RNG; the input script
 * draws from its own {@link Rng} seeded {@code seed ^ 0x51}, never from the match RNG.
 */
public final class HeadlessMatch {

    /** Seed salt of the scripted pseudo-player's own RNG. */
    public static final long SCRIPT_SALT = 0x51L;

    /** Produces a seat's input for a step; {@code rng} is the script's own generator. */
    @FunctionalInterface
    public interface InputScript {
        SeatInput input(int tick, int seat, Rng rng);

        /** Never presses anything. */
        static InputScript idle() {
            return (tick, seat, rng) -> SeatInput.NONE;
        }

        /** A pseudo-player that changes direction and buttons every 3–9 ticks. */
        static InputScript randomWalk() {
            return new RandomWalk();
        }
    }

    private static final class RandomWalk implements InputScript {
        private final SeatInput[] held = new SeatInput[Budgets.MAX_SEATS];
        private final int[] left = new int[Budgets.MAX_SEATS];

        RandomWalk() {
            Arrays.fill(held, SeatInput.NONE);
        }

        @Override
        public SeatInput input(int tick, int seat, Rng rng) {
            if (left[seat] <= 0) {
                int buttons = switch (rng.nextInt(5)) {
                    case 1 -> SeatInput.UP;
                    case 2 -> SeatInput.DOWN;
                    case 3 -> SeatInput.LEFT;
                    case 4 -> SeatInput.RIGHT;
                    default -> 0;
                };
                if (rng.chance(1, 3)) {
                    buttons |= SeatInput.A;
                }
                if (rng.chance(1, 6)) {
                    buttons |= SeatInput.B;
                }
                held[seat] = SeatInput.of(buttons, 0, 0);
                left[seat] = rng.nextInt(3, 9);
            }
            left[seat]--;
            return held[seat];
        }
    }

    private final GameDef def;
    private final ArcadeRuntime runtime;
    private final long seed;
    private final int seats;
    private final InputLog log = new InputLog();
    private final SeatInput[] lastLogged = new SeatInput[Budgets.MAX_SEATS];
    private final Rng scriptRng;
    private SnapshotCodec.Stream stream;
    private final List<Snapshot> snapshots = new ArrayList<>();

    private HeadlessMatch(ResolvedGame game, int seats, long seed) {
        this.def = game.def();
        this.seed = seed;
        this.seats = seats;
        MatchState state = ArcadeRuntime.newState(game, seats);
        this.runtime = new ArcadeRuntime(game, state, Rng.view(state));
        this.scriptRng = new Rng(seed ^ SCRIPT_SALT);
        Arrays.fill(lastLogged, SeatInput.NONE);
        runtime.start(seed);
    }

    /**
     * Validates, resolves, allocates and starts a match with {@code seed} (the cartridge's own fixed seed, if
     * any, is the caller's choice to pass).
     *
     * @throws IllegalArgumentException if the cartridge does not validate
     */
    public static HeadlessMatch of(GameDef def, int seats, long seed) {
        try {
            return of(CartridgeValidator.validate(def), seats, seed);
        } catch (ValidationError invalid) {
            throw new IllegalArgumentException("cartridge does not validate: " + invalid.getMessage(), invalid);
        }
    }

    public static HeadlessMatch of(ResolvedGame game, int seats, long seed) {
        return new HeadlessMatch(game, seats, seed);
    }

    /** Records what a viewer group would receive (keyframes + deltas at {@code snapshotHz}). */
    public HeadlessMatch withSnapshots() {
        if (stream == null) {
            stream = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        }
        return this;
    }

    /**
     * One base tick; missing seats are {@link SeatInput#NONE}. The runtime opponent's seat is never logged and
     * always passed as NONE (the runtime drives it; a replay re-derives it).
     */
    public HeadlessMatch step(SeatInput... inputs) {
        int tick = runtime.steps();
        int count = Math.min(inputs.length, runtime.state().seatCount());
        SeatInput[] row = new SeatInput[count];
        for (int s = 0; s < count; s++) {
            if (s == runtime.opponentSeat()) {
                row[s] = SeatInput.NONE;
                continue;
            }
            row[s] = inputs[s] == null ? SeatInput.NONE : inputs[s];
            log.record(tick, s, row[s], lastLogged[s]);
            lastLogged[s] = row[s];
        }
        runtime.step(row);
        if (stream != null && runtime.steps() % Math.max(1, Budgets.TICKS_PER_SECOND / Budgets.snapshotHz()) == 0) {
            stream.next(runtime.state()).ifPresent(snapshots::add);
        }
        return this;
    }

    /** {@code ticks} steps with inputs from {@code script} for every seat. */
    public HeadlessMatch run(int ticks, InputScript script) {
        for (int t = 0; t < ticks; t++) {
            stepScripted(script);
        }
        return this;
    }

    /** Steps until the match ends or {@code maxTicks} steps have run. */
    public HeadlessMatch runToEnd(InputScript script, int maxTicks) {
        for (int t = 0; t < maxTicks && runtime.result().isEmpty(); t++) {
            stepScripted(script);
        }
        return this;
    }

    private void stepScripted(InputScript script) {
        int count = runtime.state().seatCount();
        SeatInput[] row = new SeatInput[count];
        for (int s = 0; s < count; s++) {
            row[s] = s == runtime.opponentSeat() ? SeatInput.NONE : script.input(runtime.steps(), s, scriptRng);
        }
        step(row);
    }

    public MatchState state() {
        return runtime.state();
    }

    public ArcadeRuntime runtime() {
        return runtime;
    }

    public InputLog log() {
        return log;
    }

    /** This run so far as a replay. */
    public Replay toReplay() {
        return new Replay(def, seed, seats, log.copy(), hash(), runtime.steps());
    }

    public long hash() {
        return runtime.state().stateHash();
    }

    public Optional<MatchResult> result() {
        return runtime.result();
    }

    /** Snapshots recorded since {@link #withSnapshots()}. */
    public List<Snapshot> snapshotsSoFar() {
        return Collections.unmodifiableList(snapshots);
    }
}
