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

import za.co.neroland.neroarcade.board.RecordOutcome;
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
 * One running game on one bus (003 §1, §4). A solo cabinet is a bus of one ({@code busId 0}, seat 0); a Link Bus
 * host is the same class with up to four bus seats, so there is no "solo path" and no "bus path".
 *
 * <p>Owns the {@link ArcadeRuntime}, the seat table (index = bus seat = the cabinet's index on the bus) and the
 * phase machine {@code IDLE → LOBBY → COUNTDOWN → PLAYING → RESULTS → (LOBBY | IDLE)}. On a one-seat bus the use
 * that binds the seat <i>is</i> the start; with more seats every bound seat presses READY (START) and the match
 * counts down once the MULTIPLAYER chip's minimum is bound and ready. A {@code versus}-style chip that can field a
 * runtime opponent starts with one ready human after {@link #OPPONENT_WAIT_TICKS} in the lobby.
 *
 * <p><b>Runtime seats.</b> At COUNTDOWN the bound bus seats are packed in ascending order into runtime seats
 * 0..n-1 (two players at cabinets 0 and 2 play as P1 and P2). Phase packets carry the recipient's runtime seat from
 * COUNTDOWN to RESULTS and its bus seat before that. A <b>hot-seat</b> game ({@link HotSeat}) binds one controller
 * on the host cabinet only; its input drives whichever runtime seat is on turn, the controller may change hands
 * mid-match, and the result is never credited (003 §4.5).
 *
 * <p><b>Invariants:</b> {@link #finish} is the only caller of {@link BoardWriter#record}, runs at most once per
 * match and never for an abandoned or hot-seat match. The runtime never sees a player: seats are indexes; this
 * class maps them to UUIDs only for the {@link BusMatchResult}. Server thread only.
 */
public final class MatchHost {

    /** How long the results strip stays up (003 §4). */
    public static final int RESULTS_TICKS = 200;
    /** A lobby with one ready human and a free cabinet waits this long for a second human (003 §4). */
    public static final int OPPONENT_WAIT_TICKS = 200;

    private final HostEnv env;
    private final BlockPos hostPos;
    private final ResolvedGame game;
    private final BoardWriter writer;
    private final boolean hotSeat;
    private final boolean runtimeOpponent;
    private final Seat[] seats = new Seat[Budgets.MAX_SEATS];
    private final int[] lastButtons = new int[Budgets.MAX_SEATS];
    /** Who played each runtime seat this match (null: nobody, the opponent, or a hot-seat match). */
    private final UUID[] matchSeats = new UUID[Budgets.MAX_SEATS];
    /** Bus seat → runtime seat for the current match; -1 when not playing. */
    private final int[] busToRuntime = new int[Budgets.MAX_SEATS];
    private int[] runtimeToBus = new int[0];

    private int seatCount;
    private int pendingSeatCount = -1;
    private BusPhase phase = BusPhase.IDLE;
    private int matchId;
    private @Nullable ArcadeRuntime runtime;
    private long seed;
    private int phaseTicks;
    private int lobbyWait;
    private boolean finished;
    private boolean paused;
    private int newBestMask;
    private @Nullable MatchResult lastResult;
    private @Nullable SnapshotCodec.Stream stream;
    private Set<UUID> knownViewers = new HashSet<>();
    private int ticks;
    private MatchListener listener = MatchListener.NONE;
    /** The Exchange counts one play per cartridge insertion (003 §8.1); a host lives for one insertion. */
    private boolean playCounted;

    /**
     * @param busSeats seats this bus can bind: 1 for a solo cabinet or a bench test play, else
     *                 {@link #busSeatsFor(ResolvedGame, int)}
     */
    public MatchHost(HostEnv env, BlockPos hostPos, ResolvedGame game, BoardWriter writer, int busSeats) {
        this.env = env;
        this.hostPos = hostPos.immutable();
        this.game = game;
        this.writer = writer == null ? BoardWriter.NONE : writer;
        this.hotSeat = HotSeat.is(game);
        this.runtimeOpponent = game.multiplayer().runtimeOpponent(game.multiplayerProps());
        this.seatCount = clampSeats(busSeats);
        Arrays.fill(busToRuntime, -1);
    }

    /** Who hears about finished matches (events, gates, plays, seams); {@link MatchListener#NONE} by default. */
    public MatchHost listener(MatchListener value) {
        this.listener = value == null ? MatchListener.NONE : value;
        return this;
    }

    /** A solo (loopback) host: one seat, starts on use. */
    public static MatchHost solo(HostEnv env, BlockPos hostPos, ResolvedGame game, BoardWriter writer) {
        return new MatchHost(env, hostPos, game, writer, 1);
    }

    /** Bus seats for {@code game} on a bus of {@code cabinets}: hot-seat = 1, else min(chip seats, cabinets). */
    public static int busSeatsFor(ResolvedGame game, int cabinets) {
        if (HotSeat.is(game)) {
            return 1;
        }
        return Math.max(1, Math.min(Math.min(game.seats(), cabinets), Budgets.MAX_SEATS));
    }

    private int clampSeats(int n) {
        return hotSeat ? 1 : Math.max(1, Math.min(Budgets.MAX_SEATS, n));
    }

    // --- read -----------------------------------------------------------------------------------

    public BusPhase phase() {
        return phase;
    }

    /** Increments per match on this host; input and snapshot packets carry it; a mismatch is dropped. */
    public int matchId() {
        return matchId;
    }

    /** Bus seats that can be bound now (1 on a solo cabinet). */
    public int seatCount() {
        return seatCount;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    public ResolvedGame game() {
        return game;
    }

    /** True for a hot-seat (turns) game: one controller at the host cabinet, never credited. */
    public boolean hotSeat() {
        return hotSeat;
    }

    /** A copy of the seat table (index = bus seat; null = empty). */
    public Seat[] seats() {
        return Arrays.copyOf(seats, seatCount);
    }

    public @Nullable Seat seat(int index) {
        return index >= 0 && index < seatCount ? seats[index] : null;
    }

    public boolean hasSeat() {
        return boundCount() > 0;
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

    /** The runtime seat bus seat {@code busSeat} plays this match, or -1. */
    public int runtimeSeatOf(int busSeat) {
        return busSeat >= 0 && busSeat < busToRuntime.length ? busToRuntime[busSeat] : -1;
    }

    /** Runtime seats that set a personal best in the last finished match (bit = runtime seat). */
    public int newBestMask() {
        return newBestMask;
    }

    /** Seats needed to leave LOBBY: the MULTIPLAYER chip's seat count, capped by the bus (hot-seat: 1). */
    public int minimumSeats() {
        return hotSeat ? 1 : Math.max(1, Math.min(game.seats(), seatCount));
    }

    // --- bus size -------------------------------------------------------------------------------

    /**
     * The bus gained or lost cabinets. Applied at once when no match runs (seats beyond the new count are
     * unbound), otherwise when the match ends.
     */
    public void setBusSeats(int busSeats) {
        int n = clampSeats(busSeats);
        if (phase.running()) {
            pendingSeatCount = n;
            return;
        }
        applySeatCount(n);
    }

    private void applySeatCount(int n) {
        pendingSeatCount = -1;
        if (n == seatCount) {
            return;
        }
        for (int s = n; s < seatCount; s++) {
            if (seats[s] != null) {
                Seat leaving = seats[s];
                seats[s] = null;
                env.send(leaving.playerUuid, phasePayload().forSeat(-1, hostPos));
            }
        }
        seatCount = n;
        if (phase == BusPhase.LOBBY && !hasSeat()) {
            phase = BusPhase.IDLE;
        }
        broadcastPhase();
    }

    // --- seats ----------------------------------------------------------------------------------

    /**
     * Binds a seat. Allowed in IDLE and LOBBY, and in RESULTS only when nobody is seated (a fresh player at a
     * cabinet whose last player left). Never while a match runs — except the hot-seat controller, which may change
     * hands mid-match.
     *
     * @return false if the seat index is out of range or taken, or the phase refuses
     */
    public boolean bind(int index, Seat s) {
        if (index < 0 || index >= seatCount || seats[index] != null || s == null) {
            return false;
        }
        if (phase.running() && !hotSeat) {
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
        if (phase == BusPhase.LOBBY && autoStart() && boundCount() >= minimumSeats()) {
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
        env.send(leaving.playerUuid, phasePayload().forSeat(-1, hostPos));
        switch (phase) {
            case LOBBY -> {
                if (!hasSeat()) {
                    phase = BusPhase.IDLE;
                } else {
                    maybeStartFromLobby();
                }
            }
            case COUNTDOWN -> {
                if (!hotSeat) {
                    dropRuntimeSeat(index);
                    if (boundCount() < minimumSeats()) {
                        abortToLobby();
                    }
                }
            }
            case PLAYING -> {
                if (!hotSeat) {
                    // the walk-out: the rule decides the game consequence; no human left = ABANDONED
                    dropRuntimeSeat(index);
                    if (!hasSeat()) {
                        end(MatchResult.Outcome.ABANDONED);
                        return;
                    }
                }
                // hot-seat: the controller left; the match keeps running for the next player (003 §4.5)
            }
            default -> {
            }
        }
        broadcastPhase();
    }

    private void dropRuntimeSeat(int busSeat) {
        int r = runtimeSeatOf(busSeat);
        if (runtime != null && r >= 0) {
            runtime.dropSeat(r);
        }
        if (busSeat >= 0 && busSeat < busToRuntime.length) {
            busToRuntime[busSeat] = -1;
        }
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

    /** START: ready toggle in LOBBY; rematch in RESULTS (host seat only — seat 0, else the lowest bound seat). */
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
                    other.ready = false; // a fresh consent per match
                }
            }
            runtime = null;
            stream = null;
            phase = BusPhase.LOBBY;
            applyPending();
            if (autoStart() && boundCount() >= minimumSeats()) {
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
            case LOBBY -> opponentWait();
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
     * Forces the match to end (host broken, cartridge removed, unload, power timeout, sleep, hub broken). A
     * running match ends {@code forced} — normally {@code ABANDONED} — with no leaderboard write.
     */
    public void end(MatchResult.Outcome forced) {
        if (!phase.running() || runtime == null) {
            return;
        }
        int[] scores = scoresOf(runtime);
        lastResult = new MatchResult(forced, 0, scores, runtime.state().tick(), runtime.state().stateHash());
        finished = true;
        newBestMask = 0;
        phase = BusPhase.RESULTS;
        phaseTicks = RESULTS_TICKS;
        paused = false;
        broadcastPhase();
    }

    /** True when bus seats other than seat 0 play in the running match (hub broken → the match cannot go on). */
    public boolean busSeatsInMatch() {
        if (!phase.running()) {
            return false;
        }
        for (int s = 1; s < busToRuntime.length; s++) {
            if (busToRuntime[s] >= 0) {
                return true;
            }
        }
        return false;
    }

    // --- internals ------------------------------------------------------------------------------

    private boolean autoStart() {
        return seatCount == 1;
    }

    private void opponentWait() {
        boolean waiting = runtimeOpponent && !hotSeat && seatCount > 1 && boundCount() == 1 && allBoundReady();
        if (!waiting) {
            lobbyWait = 0;
            return;
        }
        if (++lobbyWait >= OPPONENT_WAIT_TICKS) {
            startCountdown();
            broadcastPhase();
        }
    }

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
        int n = rt.state().seatCount();
        SeatInput[] row = new SeatInput[Math.min(n, Budgets.MAX_SEATS)];
        for (int r = 0; r < row.length; r++) {
            Seat s;
            if (hotSeat) {
                s = seats[0]; // the controller drives whoever is on turn; the chip masks the rest
            } else {
                s = r < runtimeToBus.length && busToRuntime[runtimeToBus[r]] == r ? seats[runtimeToBus[r]] : null;
            }
            row[r] = s == null ? SeatInput.NONE : s.latched;
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
        newBestMask = 0;
        lobbyWait = 0;
        Arrays.fill(matchSeats, null);
        Arrays.fill(busToRuntime, -1);
        int runtimeSeats;
        if (hotSeat) {
            runtimeToBus = new int[] {0};
            busToRuntime[0] = 0;
            runtimeSeats = game.seats(); // turn seats are not identities: nothing is credited
        } else {
            List<Integer> bound = new ArrayList<>();
            for (int s = 0; s < seatCount; s++) {
                if (seats[s] != null) {
                    bound.add(s);
                }
            }
            runtimeToBus = new int[bound.size()];
            for (int r = 0; r < runtimeToBus.length; r++) {
                int bus = bound.get(r);
                runtimeToBus[r] = bus;
                busToRuntime[bus] = r;
                matchSeats[r] = seats[bus].playerUuid;
            }
            runtimeSeats = Math.max(1, runtimeToBus.length);
        }
        seed = game.def().seed().orElseGet(env::newSeed);
        ArcadeRuntime rt = new ArcadeRuntime(game, ArcadeRuntime.newState(game, runtimeSeats), null);
        rt.start(seed);
        runtime = rt;
        stream = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        phase = BusPhase.COUNTDOWN;
    }

    /** The only leaderboard write path; once per match; never for an abandoned or hot-seat result. */
    private void finish(MatchResult r) {
        if (finished) {
            return;
        }
        finished = true;
        lastResult = r;
        phase = BusPhase.RESULTS;
        phaseTicks = RESULTS_TICKS;
        newBestMask = 0;
        if (r.outcome() == MatchResult.Outcome.ABANDONED) {
            broadcastPhase(); // an abandoned match skips everything but the phase packet (003 §6.1)
            return;
        }
        RecordOutcome written = RecordOutcome.NONE;
        if (!hotSeat) {
            RecordOutcome outcome = writer.record(toBusResult(r));
            written = outcome == null ? RecordOutcome.NONE : outcome;
            newBestMask = written.newBestMask();
        }
        boolean firstPlay = !playCounted;
        playCounted = true;
        try {
            listener.onMatchEnd(new MatchListener.MatchEnd(toBusResult(r), game, written, hotSeat, firstPlay, hostPos));
        } catch (RuntimeException e) {
            // a listener (events, seams) must never take the match down with it
            za.co.neroland.neroarcade.NeroArcadeCommon.LOGGER.warn("[NeroArcade] match-end listener failed ({})",
                    e.getClass().getName());
        }
        broadcastPhase();
    }

    private BusMatchResult toBusResult(MatchResult r) {
        int[] scores = r.scores();
        int n = runtime == null ? runtimeToBus.length : runtime.state().seatCount();
        int[] ranks = ranks(scores, n);
        int dropped = runtime == null ? 0 : runtime.state().droppedSeatMask();
        int opponent = runtime == null ? -1 : runtime.opponentSeat();
        List<BusMatchResult.SeatResult> lines = new ArrayList<>();
        for (int s = 0; s < n && s < matchSeats.length; s++) {
            if (matchSeats[s] == null || s == opponent) {
                continue;
            }
            int score = s < scores.length ? scores[s] : 0;
            lines.add(new BusMatchResult.SeatResult(matchSeats[s], s, score, ranks[s], (dropped & (1 << s)) != 0));
        }
        return new BusMatchResult(game.def().lineageRoot(), game.def().id(), lines, r.ticks(), seed, r.outcome(),
                BusMatchResult.EndedBy.RULE, r.finalHash());
    }

    private boolean allBoundReady() {
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null && !seats[s].ready) {
                return false;
            }
        }
        return true;
    }

    private void maybeStartFromLobby() {
        if (phase != BusPhase.LOBBY || boundCount() < minimumSeats() || !allBoundReady()) {
            return;
        }
        startCountdown();
    }

    private void abortToLobby() {
        runtime = null;
        stream = null;
        Arrays.fill(busToRuntime, -1);
        for (Seat s : seats) {
            if (s != null) {
                s.ready = false;
            }
        }
        phase = hasSeat() ? BusPhase.LOBBY : BusPhase.IDLE;
        applyPending();
    }

    private void toLobbyOrIdle() {
        abortToLobby();
    }

    private void applyPending() {
        if (pendingSeatCount > 0 && !phase.running()) {
            applySeatCount(pendingSeatCount);
        }
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
                sendPhase(viewer, phasePayload());
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
        // one viewer group per bus: every node, marquee and seat hears the host's frame verbatim (003 §3.2)
        SnapshotPayload payload = new SnapshotPayload(hostPos, matchId, next.get().body());
        for (UUID viewer : viewers) {
            env.send(viewer, payload);
        }
    }

    private Set<UUID> recipients() {
        Set<UUID> out = new LinkedHashSet<>(env.viewers());
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null) {
                out.add(seats[s].playerUuid);
            }
        }
        return out;
    }

    private void broadcastPhase() {
        MatchPhasePayload base = phasePayload();
        for (UUID recipient : recipients()) {
            sendPhase(recipient, base);
        }
    }

    private void sendPhase(UUID recipient, MatchPhasePayload base) {
        int bus = busSeatOf(recipient);
        BlockPos at = bus >= 0 && seats[bus].at != null ? seats[bus].at : hostPos;
        env.send(recipient, base.forSeat(mySeat(bus), at));
    }

    /** The seat number a recipient sees: its runtime seat while a match is on, else its bus seat. */
    private int mySeat(int busSeat) {
        if (busSeat < 0) {
            return -1;
        }
        boolean inMatch = phase.running() || (phase == BusPhase.RESULTS && lastResult != null);
        if (!inMatch) {
            return busSeat;
        }
        if (hotSeat) {
            return 0;
        }
        int r = runtimeSeatOf(busSeat);
        return r >= 0 ? r : -1;
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
        int[] scores = new int[0];
        int[] ranks = new int[0];
        int outcome = -1;
        int best = 0;
        if (phase == BusPhase.RESULTS && lastResult != null) {
            int[] all = lastResult.scores();
            int n = Math.min(all.length, Budgets.MAX_SEATS);
            scores = Arrays.copyOf(all, n);
            ranks = ranks(scores, n);
            outcome = lastResult.outcome().ordinal();
            best = newBestMask;
        }
        int ticksLeft = switch (phase) {
            case COUNTDOWN -> runtime == null ? 0 : runtime.state().countdownTicks();
            case RESULTS -> Math.max(0, phaseTicks);
            default -> 0;
        };
        return new MatchPhasePayload(hostPos, matchId, phase, seatCount, bound, ready, -1, ranks, scores, best,
                ticksLeft, paused, outcome, hostPos);
    }

    private HudLegendPayload legend() {
        return new HudLegendPayload(hostPos, game.control().legend(game.controlProps()), game.rule().hudLangKey(),
                game.rule().hudArgs(game.ruleProps()));
    }

    private int busSeatOf(UUID player) {
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null && seats[s].playerUuid.equals(player)) {
                return s;
            }
        }
        return -1;
    }

    private int boundCount() {
        int n = 0;
        for (int s = 0; s < seatCount; s++) {
            if (seats[s] != null) {
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
