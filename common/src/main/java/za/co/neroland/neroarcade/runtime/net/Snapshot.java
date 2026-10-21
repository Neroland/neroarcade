package za.co.neroland.neroarcade.runtime.net;

import java.util.Arrays;

/**
 * One encoded screen update: a keyframe (full state) or a delta on the previous update. {@code body} is the
 * {@link SnapshotCodec} output carried by {@link SnapshotPayload}.
 *
 * @param baseTick the tick of the update a delta builds on; equals {@code tick} for a keyframe
 */
public record Snapshot(boolean keyframe, int baseTick, int tick, byte[] body) {

    public Snapshot {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public int size() {
        return body.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Snapshot that && keyframe == that.keyframe && baseTick == that.baseTick
                && tick == that.tick && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return 31 * tick + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return (keyframe ? "Keyframe" : "Delta") + "[tick=" + tick + ", base=" + baseTick + ", " + body.length + " bytes]";
    }
}
