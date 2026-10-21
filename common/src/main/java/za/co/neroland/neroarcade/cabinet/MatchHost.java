package za.co.neroland.neroarcade.cabinet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.ArcadeRuntime;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchPhase;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/**
 * One running game on one bus (003 §1, §4). A solo cabinet is a bus of one ({@code busId 0}, seat 0); Stage 4
 * adds joining, so nothing here has a "solo path".
 *
 * <p>Owns the {@link ArcadeRuntime}, the seat table and the phase machine
 * {@code IDLE → LOBBY → COUNTDOWN → PLAYING → RESULTS → (LOBBY | IDLE)}. With {@code autoStart} (a solo bus) the
 * use that binds the seat <i>is</i> the start. Sends {@code MatchPhasePayload} on every phase change and to new
 * viewers, {@code HudLegendPayload} at bind, and {@code SnapshotPayload}s at {@code runtime.snapshotHz} with a
 * keyframe every {@code runtime.keyframeTicks} and whenever a new viewer arrives.
 *
 * <p><b>Invariants:</b> {@link #finish} is the only caller of {@link BoardWriter#record}, runs at most once per
 * match and never for an abandoned match. The runtime never sees a player: seats are indexes; this class maps
 * them to UUIDs only for the {@link BusMatchResult}. Server thread only.
 */
public final class MatchHost {

    /** How long the results strip stays up (003 §4). */
    public static final int RESULTS_TICKS = 200;

    private final HostEnv env;
    private final BlockPos hostPos;
    private final ResolvedGame game;
    private final BoardWriter writer;
    private final int seatCount;
    private final boolean autoStart;
    private final Seat[] seats;
    /** Who was seated when the match started (index = seat), for the result; null when nobody. */
    private final UUID[] matchSeats;
    private final int[] lastButtons;

    private BusPhase phase = BusPhase.IDLE;
    private int matchId;
    private @Nullable ArcadeRuntime runtime;
    private long seed;
    private int phaseTicks;
    private boolean finished;
    private boolean paused;
    private @Nullable MatchResult lastResult;
    private @Nullable SnapshotCodec.Stream stream;
    private Set<UUID> knownViewers = new HashSet<>();
    private int ticks;

    /**
     * @param busSeats  seats on this bus: 1 for a solo cabinet or a bench test play
     * @param autoStart true on a one-seat bus: binding the seat starts the countdown
     */
    public MatchHost(HostEnv env, BlockPos hostPos, ResolvedGame game, BoardWriter writer, int busSeats,
            boolean autoStart) {
        this.env = env;
        this.hostPos = hostPos.immutable();
        this.game = game;
        this.writer = writer == null ? BoardWriter.NONE : writer;
        this.seatCount = Math.max(1, Math.min(Budgets.MAX_SEATS, busSeats));
        this.autoStart = autoStart;
        this.seats = new Seat[seatCount];
        this.matchSeats = new UUID[Budgets.MAX_SEATS];
        this.lastButtons = new int[seatCount];
    }

    /** A solo (loopback) host: one seat, starts on use. */
    public static MatchHost solo(HostEnv env, BlockPos hostPos, ResolvedGame game, BoardWriter writer) {
        return new MatchHost(env, hostPos, game, writer, 1, true);
    }

    // --- read -----------------------------------------------------------------------------------

    public BusPhase phase() {
        return phase;
    }

    /** Increments per match on this host; input and snapshot packets carry it; a mismatch is dropped. */
    public int matchId() {
        return matchId;
    }

    public int seatCount() {
        return seatCount;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    public ResolvedGame game() {
        return game;
    }

    /** A copy of the seat table (index = bus seat; null = empty). */
    public Seat[] seats() {
        return seats.clone();
    }

    public @Nullable Seat seat(int index) {
        return index >= 0 && index < seatCount ? seats[index] : null;
    }

    public boolean hasSeat() {
        for (Seat s : seats) {
            if (s != null) {
                return true;
            }
        }
        return false;
    }

    public Optional<MatchResult> lastResult() {
        return Optional.ofNullable(lastResult);
    }

    public @Nullable ArcadeRuntime runtime() {
        return runtime;
    }

    public boolean paused() {
        return paused;
    }

    /** Seats needed to leave LOBBY: the MULTIPLAYER chip's seat count, capped by the bus. */
    public int minimumSeats() {
        return Math.max(1, Math.min(game.seats(), seatCount));
    }

    // --- seats ----------------------------------------------------------------------------------

    /**
     * Binds a seat. Allowed in IDLE and LOBBY, and in RESULTS only when nobody is seated (a fresh player at a
     * solo cabinet whose last player left). Never while a match runs (a late joiner never enters a running match).
     *
     * @return false if the seat index is out of range or taken, or the phase refuses
     */
    public boolean bind(int index, Seat s) {
        if (index < 0 || index >= seatCount || seats[index] != null || s == null || phase.running()) {
            return false;
        }
        if (phase == BusPhase.RESULTS) {
            if (hasSeat()) {
                return false;
            }
            toLobbyOrIdle();
        }
        seats[index] = s;
        s.ready = false;
        lastButtons[index] = s.latched.buttons();
        env.send(s.playerUuid, legend());
        if (phase == BusPhase.IDLE) {
            phase = BusPhase.LOBBY;
        }
        if (autoStart && boundCount() >= minimumSeats()) {
            startCountdown();
        }
        broadcastPhase();
        return true;
    }

    /** Unbinds a seat in any phase (003 §2.3, §4.4). The leaver gets a phase packet with {@code mySeat = -1}. */
    public void unbind(int index, DropReason why) {
        if (index < 0 || index >= seatCount || seats[index] == null) {
            return;
        }
        Seat leaving = seats[index];
        seats[index] = null;
        env.send(leaving.playerUuid, phasePayload().forSeat(-1));
        switch (phase) {
            case LOBBY -> {
                if (!hasSeat()) {
                    phase = BusPhase.IDLE;
                } else {
                    maybeStartFromLobby();
                }
            }
            case COUNTDOWN -> {
                if (runtime != null) {
                    runtime.dropSeat(index);
                }
                if (boundCount() < minimumSeats()) {
                    runtime = null;
                    stream = null;
                    phase = hasSeat() ? BusPhase.LOBBY : BusPhase.IDLE;
                }
            }
            case PLAYING -> {
                if (runtime != null) {
                    runtime.dropSeat(index);
                }
                if (!hasSeat()) {
                    end(MatchResult.Outcome.ABANDONED);
                    return;
                }
            }
            default -> {
            }
        }
        broadcastPhase();
    }

    /** Latches a seat's input for the next tick boundary (the caller has passed {@link InputGate}). */
    public void input(int index, SeatInput in) {
        Seat s = seat(index);
        if (s != null) {
            s.latched = in == null ? SeatInput.NONE : in;
        }
    }

    /**
     * Runs {@link InputGate} for the seat at {@code index} and latches on success. The caller counts
     * {@code NO_SEAT}/{@code WRONG_PLAYER} on its cabinet.
     */
    public InputGate.Verdict offer(int index, UUID sender, int packetMatchId, SeatInput in) {
        return InputGate.offer(seat(index), sender, packetMatchId, matchId, in, env.gameTime(),
                Budgets.maxInputPacketsPerSecond());
    }

    /** START: ready toggle in LOBBY; rematch in RESULTS (host seat only — the lowest bound seat). */
    public void pressStart(int index) {
        Seat s = seat(index);
        if (s == null) {
            return;
        }
        if (phase == BusPhase.LOBBY) {
            s.ready = !s.ready;
            maybeStartFromLobby();
            broadcastPhase();
        } else if (phase == BusPhase.RESULTS && index == lowestBoundSeat()) {
            for (Seat other : seats) {
                if (other != null) {
                    other.ready = false;
                }
            }
            phase = BusPhase.LOBBY;
            if (autoStart && boundCount() >= minimumSeats()) {
                startCountdown();
            }
            broadcastPhase();
        }
    }

    // --- tick -----------------------------------------------------------------------------------

    /** One server tick: START edges, the phase machine, a runtime step, snapshots and new viewers. */
    public void tick() {
        ticks++;
        startEdges();
        switch (phase) {
            case COUNTDOWN, PLAYING -> stepRuntime();
            case RESULTS -> {
                if (--phaseTicks <= 0) {
                    toLobbyOrIdle();
                    broadcastPhase();
                }
            }
            default -> {
            }
        }
        int interval = Math.max(1, Budgets.TICKS_PER_SECOND / Math.max(1, Budgets.snapshotHz()));
        if (ticks % interval == 0) {
            sendToViewers();
        }
    }

    /**
     * Forces the match to end (host broken, cartridge removed, unload, power timeout, sleep). A running match
     * ends {@code forced} — normally {@code ABANDONED} — with no leaderboard write.
     */
    public void end(MatchResult.Outcome forced) {
        if (!phase.running() || runtime == null) {
            return;
        }
        int[] scores = scoresOf(runtime);
        lastResult = new MatchResult(forced, 0, scores, runtime.state().tick(), runtime.state().stateHash());
        finished = true;
        phase = BusPhase.RESULTS;
        phaseTicks = RESULTS_TICKS;
        paused = false;
        broadcastPhase();
    }

    // --- internals ------------------------------------------------------------------------------

    private void stepRuntime() {
        ArcadeRuntime rt = runtime;
        if (rt == null) {
            toLobbyOrIdle();
            return;
        }
        boolean powered = env.powered();
        if (paused == powered) {
            paused = !powered;
            broadcastPhase();
        }
        if (!powered) {
            return;
        }
        SeatInput[] row = new SeatInput[seatCount];
        for (int s = 0; s < seatCount; s++) {
            row[s] = seats[s] == null ? SeatInput.NONE : seats[s].latched;
        }
        rt.step(row);
        if (phase == BusPhase.COUNTDOWN && rt.state().phase() == MatchPhase.PLAYING) {
            phase = BusPhase.PLAYING;
            broadcastPhase();
        }
        Optional<MatchResult> result = rt.result();
        if (result.isPresent()) {
            finish(result.get());
        }
    }

    private void startCountdown() {
        matchId++;
        finished = false;
        lastResult = null;
        paused = false;
        Arrays.fill(matchSeats, null);
        for (int s = 0; s < seatCount; s++) {
            matchSeats[s] = seats[s] == null ? null : seats[s].playerUuid;
        }
        seed = game.def().seed().orElseGet(env::newSeed);
        ArcadeRuntime rt = new ArcadeRuntime(game, ArcadeRuntime.newState(game, seatCount), null);
        rt.start(seed);
        runtime = rt;
        stream = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        phase = BusPhase.COUNTDOWN;
    }

    /** The only leaderboard write path; once per match; never for an abandoned result. */
    private void finish(MatchResult r) {
        if (finished) {
            return;
        }
        finished = true;
        lastResult = r;
        phase = BusPhase.RESULTS;
        phaseTicks = RESULTS_TICKS;
        if (r.outcome() != MatchResult.Outcome.ABANDONED) {
            writer.record(toBusResult(r));
        }
        broadcastPhase();
    }

    private BusMatchResult toBusResult(MatchResult r) {
        int[] scores = r.scores();
        int[] ranks = ranks(scores, seatCount);
        int dropped = runtime == null ? 0 : runtime.state().droppedSeatMask();
        int opponent = runtime == null ? -1 : runtime.opponentSeat();
        List<BusMatchResult.SeatResult> lines = new ArrayList<>();
        for (int s = 0; s < seatCount; s++) {
            if (matchSeats[s] == null || s == opponent) {
                continue;
            }
            int score = s < scores.length ? scores[s] : 0;
            lines.add(new BusMatchResult.SeatResult(matchSeats[s], s, score, ranks[s], (dropped & (1 << s)) != 0));
        }
        return new BusMatchResult(game.def().lineageRoot(), game.def().id(), lines, r.ticks(), seed, r.outcome(),
                BusMatchResult.EndedBy.RULE, r.finalHash());
    }

    private void maybeStartFromLobby() {
        if (phase != BusPhase.LOBBY || boundCount() < minimumSeats()) {
            return;
        }
        for (Seat s : seats) {
            if (s != null && !s.ready) {
                return;
            }
        }
        startCountdown();
    }

    private void toLobbyOrIdle() {
        runtime = null;
        stream = null;
        paused = false;
        for (Seat s : seats) {
            if (s != null) {
                s.ready = false;
            }
        }
        phase = hasSeat() ? BusPhase.LOBBY : BusPhase.IDLE;
    }

    /** START is a lobby/results button: a rising edge of the START bit on a latched input presses it. */
    private void startEdges() {
        for (int s = 0; s < seatCount; s++) {
            Seat seat = seats[s];
            if (seat == null) {
                continue;
            }
            int buttons = seat.latched.buttons();
            boolean rising = (buttons & SeatInput.START) != 0 && (lastButtons[s] & SeatInput.START) == 0;
            lastButtons[s] = buttons;
            if (rising && (phase == BusPhase.LOBBY || phase == BusPhase.RESULTS)) {
                pressStart(s);
            }
        }
    }

    private void sendToViewers() {
        Set<UUID> viewers = recipients();
        boolean newcomer = false;
        for (UUID viewer : viewers) {
            if (!knownViewers.contains(viewer)) {
                newcomer = true;
                env.send(viewer, phasePayload().forSeat(seatIndexOf(viewer)));
            }
        }
        knownViewers = viewers;
        if (runtime == null || stream == null) {
            return;
        }
        if (newcomer) {
            stream.reset(); // a new viewer must start from a keyframe (002 §8)
        }
        Optional<za.co.neroland.neroarcade.runtime.net.Snapshot> next = stream.next(runtime.state());
        if (next.isEmpty()) {
            return;
        }
        SnapshotPayload payload = new SnapshotPayload(hostPos, matchId, next.get().body());
        for (UUID viewer : viewers) {
            env.send(viewer, payload);
        }
    }

    private Set<UUID> recipients() {
        Set<UUID> out = new LinkedHashSet<>(env.viewers());
        for (Seat s : seats) {
            if (s != null) {
                out.add(s.playerUuid);
            }
        }
        return out;
    }

    private void broadcastPhase() {
        MatchPhasePayload base = phasePayload();
        for (UUID recipient : recipients()) {
            env.send(recipient, base.forSeat(seatIndexOf(recipient)));
        }
    }

    /** The phase packet as a spectator would see it ({@code mySeat = -1}). */
    public MatchPhasePayload phasePayload() {
        int bound = 0;
        int ready = 0;
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null) {
                bound |= 1 << s;
                if (seats[s].ready) {
                    ready |= 1 << s;
                }
            }
        }
        int[] scores = new int[seatCount];
        int[] ranks = new int[seatCount];
        int outcome = -1;
        if (phase == BusPhase.RESULTS && lastResult != null) {
            int[] all = lastResult.scores();
            System.arraycopy(all, 0, scores, 0, Math.min(all.length, seatCount));
            ranks = ranks(scores, seatCount);
            outcome = lastResult.outcome().ordinal();
        }
        int ticksLeft = switch (phase) {
            case COUNTDOWN -> runtime == null ? 0 : runtime.state().countdownTicks();
            case RESULTS -> Math.max(0, phaseTicks);
            default -> 0;
        };
        return new MatchPhasePayload(hostPos, matchId, phase, seatCount, bound, ready, -1, ranks, scores, 0,
                ticksLeft, paused, outcome);
    }

    private HudLegendPayload legend() {
        return new HudLegendPayload(hostPos, game.control().legend(game.controlProps()), game.rule().descLangKey(),
                new int[0]);
    }

    private int seatIndexOf(UUID player) {
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null && seats[s].playerUuid.equals(player)) {
                return s;
            }
        }
        return -1;
    }

    private int boundCount() {
        int n = 0;
        for (Seat s : seats) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    private int lowestBoundSeat() {
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null) {
                return s;
            }
        }
        return -1;
    }

    private static int[] scoresOf(ArcadeRuntime rt) {
        int[] scores = new int[rt.state().seatCount()];
        for (int s = 0; s < scores.length; s++) {
            scores[s] = rt.state().score(s);
        }
        return scores;
    }

    /** 1-based ranks by descending score; equal scores share a rank. */
    static int[] ranks(int[] scores, int count) {
        int[] ranks = new int[count];
        for (int s = 0; s < count; s++) {
            int score = s < scores.length ? scores[s] : 0;
            int better = 0;
            for (int o = 0; o < count; o++) {
                int other = o < scores.length ? scores[o] : 0;
                if (other > score) {
                    better++;
                }
            }
            ranks[s] = better + 1;
        }
        return ranks;
    }
}
