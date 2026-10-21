package za.co.neroland.neroarcade.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "I am leaving my seat" (Esc on the HUD). {@code holder} is the cabinet (or the bench during
 * a test play) the client believes it sits at; the server ignores the packet unless the sender really holds
 * the seat there.
 */
public record LeaveSeatPayload(BlockPos holder) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LeaveSeatPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "leave_seat"));

    public static final StreamCodec<ByteBuf, LeaveSeatPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(LeaveSeatPayload::new, LeaveSeatPayload::holder);

    @Override
    public CustomPacketPayload.Type<LeaveSeatPayload> type() {
        return TYPE;
    }
}
