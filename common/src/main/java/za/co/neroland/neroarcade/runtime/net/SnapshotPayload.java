package za.co.neroland.neroarcade.runtime.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: one {@link SnapshotCodec} update for a cabinet's screen. Carries no personal data — the
 * match state names seats, never players. Declared here with its codec; Stage 4 registers it on
 * {@code neroarcade:main} together with its handler.
 *
 * @param body {@link SnapshotCodec} bytes, at most {@code runtime.snapshotMaxBytes}' ceiling (16 KiB)
 */
public record SnapshotPayload(BlockPos cabinet, int matchId, byte[] body) implements CustomPacketPayload {

    /** Hard wire ceiling: the largest {@code runtime.snapshotMaxBytes} an admin can set. */
    public static final int MAX_BODY_BYTES = 16384;

    public static final CustomPacketPayload.Type<SnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "snapshot"));

    public static final StreamCodec<ByteBuf, SnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SnapshotPayload::cabinet,
            ByteBufCodecs.VAR_INT, SnapshotPayload::matchId,
            ByteBufCodecs.byteArray(MAX_BODY_BYTES), SnapshotPayload::body,
            SnapshotPayload::new);

    public SnapshotPayload {
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("snapshot body over " + MAX_BODY_BYTES + " bytes");
        }
    }

    @Override
    public CustomPacketPayload.Type<SnapshotPayload> type() {
        return TYPE;
    }
}
