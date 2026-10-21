package za.co.neroland.neroarcade.client.match;

import java.util.Optional;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.client.screen.EntityPositions;
import za.co.neroland.neroarcade.client.screen.SpriteMap;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

/**
 * The client's mirror of one host's match (a cabinet, or a bench during a test play): the last two snapshots for
 * interpolation, the phase packet, the HUD legend and — for the seated player only — the local-pawn prediction.
 * Display only: nothing here is ever sent back as a fact. Pure (no client classes), so it runs in unit tests.
 */
public final class ClientMatch {

    /** What the client may react to with a sound (never with game logic). */
    public interface Events {
        Events NONE = new Events() {
        };

        default void phaseChanged(ClientMatch match, @Nullable BusPhase from, BusPhase to) {
        }

        default void scored(ClientMatch match, int delta) {
        }

        default void hit(ClientMatch match) {
        }

        default void timerSecond(ClientMatch match, int secondsLeft) {
        }
    }

    private final BlockPos host;
    private int matchId = Integer.MIN_VALUE;
    private @Nullable MatchPhasePayload phase;
    private @Nullable HudLegendPayload legend;
    private long legendSince;
    private long phaseSince;
    private @Nullable MatchState previous;
    private @Nullable MatchState latest;
    private long previousArrival;
    private long latestArrival;
    private @Nullable ResolvedGame game;
    private SpriteMap map = SpriteMap.DEFAULT;
    private @Nullable LocalPrediction prediction;
    private int lastScore = Integer.MIN_VALUE;
    private int lastLives = Integer.MIN_VALUE;
    private int lastTimerSecond = Integer.MIN_VALUE;
    private long lastSeen;
    private final Positions positions = new Positions();

    public ClientMatch(BlockPos host) {
        this.host = host.immutable();
    }

    public BlockPos host() {
        return host;
    }

    public int matchId() {
        return matchId;
    }

    public @Nullable MatchPhasePayload phase() {
        return phase;
    }

    public BusPhase busPhase() {
        return phase == null ? BusPhase.IDLE : phase.phase();
    }

    /** The recipient's seat, or -1 (spectator / not seated). */
    public int mySeat() {
        return phase == null ? -1 : phase.mySeat();
    }

    public boolean seated() {
        return mySeat() >= 0;
    }

    public @Nullable HudLegendPayload legend() {
        return legend;
    }

    /** Client tick the legend arrived (the HUD shows it for the first 5 s of the match). */
    public long legendSince() {
        return legendSince;
    }

    /** Client tick the current phase began. */
    public long phaseSince() {
        return phaseSince;
    }

    public @Nullable MatchState latest() {
        return latest;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public @Nullable ResolvedGame game() {
        return game;
    }

    public SpriteMap spriteMap() {
        return map;
    }

    public @Nullable LocalPrediction prediction() {
        return prediction;
    }

    /** The game this host runs (from the cabinet's synced cartridge or the bench draft); enables prediction. */
    public void setGame(@Nullable ResolvedGame resolved) {
        if (resolved == game) {
            return;
        }
        game = resolved;
        map = SpriteMap.of(resolved);
        refreshPrediction();
    }

    public void onPhase(MatchPhasePayload payload, long now, Events events) {
        lastSeen = now;
        BusPhase before = phase == null ? null : phase.phase();
        if (payload.matchId() != matchId) {
            startMatch(payload.matchId());
        }
        int seatBefore = mySeat();
        phase = payload;
        if (before != payload.phase()) {
            phaseSince = now;
            events.phaseChanged(this, before, payload.phase());
        }
        if (seatBefore != mySeat()) {
            refreshPrediction();
        }
    }

    /** The player just asked to leave: stop treating this as their seat until the server's answer arrives. */
    public void forgetSeat() {
        if (phase != null && phase.mySeat() >= 0) {
            phase = phase.forSeat(-1);
            refreshPrediction();
        }
    }

    public void onLegend(HudLegendPayload payload, long now) {
        lastSeen = now;
        legend = payload;
        legendSince = now;
    }

    /**
     * A snapshot: decoded against the latest state (a delta that does not build on it is dropped until the next
     * keyframe, which the server sends at least every {@code runtime.keyframeTicks}).
     *
     * @return true if it was applied
     */
    public boolean onSnapshot(int snapshotMatchId, byte[] body, long now, Events events) {
        lastSeen = now;
        if (snapshotMatchId != matchId) {
            startMatch(snapshotMatchId);
        }
        Optional<MatchState> decoded;
        try {
            decoded = SnapshotCodec.decode(body, latest);
        } catch (RuntimeException malformed) {
            return false;
        }
        if (decoded.isEmpty()) {
            return false;
        }
        previous = latest;
        previousArrival = latestArrival;
        latest = decoded.get();
        latestArrival = now;
        if (previous == null) {
            previousArrival = now - 2;
        }
        if (prediction != null) {
            prediction.onSnapshot(latest);
        }
        watchOwnSeat(events);
        return true;
    }

    /** One client tick for the seated player: feeds the prediction its own current input. */
    public void tick(SeatInput myInput) {
        if (prediction != null && latest != null) {
            prediction.tick(myInput);
        }
    }

    /** The positions to draw at client time {@code nowFp} (ticks x 256 + partial). Reused object. */
    public EntityPositions positions(long nowFp) {
        positions.partial = Interpolation.partial(nowFp, previousArrival, latestArrival);
        return positions;
    }

    private void startMatch(int id) {
        matchId = id;
        previous = null;
        latest = null;
        lastScore = Integer.MIN_VALUE;
        lastLives = Integer.MIN_VALUE;
        lastTimerSecond = Integer.MIN_VALUE;
        refreshPrediction();
    }

    private void refreshPrediction() {
        int seat = mySeat();
        ResolvedGame resolved = game;
        prediction = seat >= 0 && resolved != null ? new LocalPrediction(resolved, seat) : null;
        if (prediction != null && latest != null) {
            prediction.onSnapshot(latest);
        }
    }

    /** Score up = a pickup or a point, lives down = a hit, the last five timer seconds tick — own seat only. */
    private void watchOwnSeat(Events events) {
        MatchState state = latest;
        int seat = mySeat();
        if (state == null || seat < 0 || seat >= state.seatCount()) {
            return;
        }
        int score = state.score(seat);
        int lives = state.lives(seat);
        if (lastScore != Integer.MIN_VALUE && score > lastScore) {
            events.scored(this, score - lastScore);
        }
        if (lastLives != Integer.MIN_VALUE && lives < lastLives) {
            events.hit(this);
        }
        lastScore = score;
        lastLives = lives;
        int timer = state.timerTicks();
        int second = timer <= 0 ? -1 : (timer + 19) / 20;
        if (second != lastTimerSecond && second >= 1 && second <= 5 && lastTimerSecond != Integer.MIN_VALUE) {
            events.timerSecond(this, second);
        }
        lastTimerSecond = second;
    }

    /** Interpolated remote entities, predicted own pawn. */
    private final class Positions implements EntityPositions {
        int partial;

        @Override
        public int x(MatchState state, int id) {
            LocalPrediction p = prediction;
            if (p != null && p.owns(state, id)) {
                return p.x(id);
            }
            MatchState before = previous;
            int now = state.get(id, Entities.X);
            return before != null && before.exists(id) ? Interpolation.lerp(before.get(id, Entities.X), now, partial)
                    : now;
        }

        @Override
        public int y(MatchState state, int id) {
            LocalPrediction p = prediction;
            if (p != null && p.owns(state, id)) {
                return p.y(id);
            }
            MatchState before = previous;
            int now = state.get(id, Entities.Y);
            return before != null && before.exists(id) ? Interpolation.lerp(before.get(id, Entities.Y), now, partial)
                    : now;
        }
    }
}
