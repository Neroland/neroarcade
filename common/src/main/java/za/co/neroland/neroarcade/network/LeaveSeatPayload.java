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

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<LeaveSeatPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "leave_seat"));
    //?} else {
    /*public static final CustomPacketPayload.Type<LeaveSeatPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "leave_seat"), LeaveSeatPayload.class);
    *///?}

    //? if >=1.20.5 {
    public static final StreamCodec<ByteBuf, LeaveSeatPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(LeaveSeatPayload::new, LeaveSeatPayload::holder);
    //?} else {
    /*public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, LeaveSeatPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeBlockPos(payload.holder()),
                    buf -> new LeaveSeatPayload(buf.readBlockPos()));
    *///?}

    @Override
    public CustomPacketPayload.Type<LeaveSeatPayload> type() {
        return TYPE;
    }
}
