package za.co.neroland.neroarcade.runtime.net;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.StateHash;

/**
 * Encodes match states for viewers: keyframes (the canonical {@link MatchState#writeKeyframe} bytes) and
 * deltas against the last update sent ({@link MatchState#writeDelta}). Big-endian throughout.
 *
 * <p><b>Invariants:</b> no update is ever split or dropped. A delta is replaced by a keyframe when it would be
 * at least as long as the keyframe, longer than the byte cap, or when a field the delta format does not carry
 * changed (countdown, winners, result, dropped seats, shared lives, topology, gravity, fog, pawn ids); the
 * keyframe always fits the cap because the cap's floor is above {@link Budgets#WORST_CASE_KEYFRAME_BYTES}.
 * Clients never receive the RNG state or status countdowns through deltas: they are not predicted.
 */
public final class SnapshotCodec {

    private SnapshotCodec() {
    }

    /** The keyframe bytes of a state. */
    public static byte[] writeKeyframe(MatchState state) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        state.writeKeyframe(out::write);
        return out.toByteArray();
    }

    /** The delta bytes from {@code base} to {@code current} (no compatibility check). */
    public static byte[] writeDelta(MatchState base, MatchState current) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        current.writeDelta(base, out::write);
        return out.toByteArray();
    }

    /**
     * Chooses keyframe or delta for {@code current} given the last update sent ({@code null} = none).
     *
     * @param forceKeyframe true on the keyframe interval or for a new viewer
     */
    public static Snapshot encode(MatchState lastSent, MatchState current, boolean forceKeyframe, int maxBytes) {
        byte[] keyframe = writeKeyframe(current);
        if (!forceKeyframe && lastSent != null && current.deltaCompatibleWith(lastSent)) {
            byte[] delta = writeDelta(lastSent, current);
            if (delta.length < keyframe.length && delta.length <= maxBytes) {
                return new Snapshot(false, lastSent.tick(), current.tick(), delta);
            }
        }
        return new Snapshot(true, current.tick(), current.tick(), keyframe);
    }

    /**
     * Decodes an update. A keyframe yields a fresh state; a delta is applied to {@code previous}.
     *
     * @return empty when a delta does not build on {@code previous} (the viewer needs a keyframe)
     * @throws IllegalArgumentException on malformed bytes
     */
    public static Optional<MatchState> decode(byte[] body, MatchState previous) {
        if (body.length == 0) {
            throw new IllegalArgumentException("empty snapshot");
        }
        ByteBuffer in = ByteBuffer.wrap(body);
        if (body[0] == 0) {
            return Optional.of(MatchState.readKeyframe(in, Budgets.HARD_MAX_ENTITIES));
        }
        if (previous == null || body.length < 5 || in.getInt(1) != previous.tick()) {
            return Optional.empty();
        }
        return Optional.of(previous.applyDelta(in));
    }

    /**
     * The send side for one viewer group (one cabinet or bus): remembers the last state sent, emits a keyframe
     * every {@code keyframeTicks}, and skips a send when nothing a viewer can see changed.
     */
    public static final class Stream {
        private final int keyframeTicks;
        private final int maxBytes;
        private MatchState lastSent;
        private int lastKeyframeTick;
        private long lastView;

        public Stream(int keyframeTicks, int maxBytes) {
            this.keyframeTicks = Math.max(1, keyframeTicks);
            this.maxBytes = maxBytes;
        }

        /** The next update for {@code current}, or empty when nothing visible changed since the last one. */
        public Optional<Snapshot> next(MatchState current) {
            long view = StateHash.ofView(current, true);
            if (lastSent != null && view == lastView) {
                return Optional.empty();
            }
            boolean force = lastSent == null || current.tick() - lastKeyframeTick >= keyframeTicks;
            Snapshot snapshot = encode(lastSent, current, force, maxBytes);
            if (snapshot.keyframe()) {
                lastKeyframeTick = current.tick();
            }
            lastSent = current.copy();
            lastView = view;
            return Optional.of(snapshot);
        }

        /** Forget what was sent: the next update is a keyframe (new viewer, match change). */
        public void reset() {
            lastSent = null;
        }
    }
}
