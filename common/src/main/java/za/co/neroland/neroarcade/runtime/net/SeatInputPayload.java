package za.co.neroland.neroarcade.runtime.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * Client → server: a seated player's latest input. A request, never a fact: the server checks the sender holds
 * the seat and rate-limits per seat (Stage 4) before the runtime sees it at the next tick boundary.
 * {@code clientTick} is only used to reconcile the client's own prediction.
 */
public record SeatInputPayload(BlockPos cabinet, int matchId, SeatInput input, int clientTick)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SeatInputPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "seat_input"));

    public static final StreamCodec<ByteBuf, SeatInputPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SeatInputPayload::cabinet,
            ByteBufCodecs.VAR_INT, SeatInputPayload::matchId,
            SeatInput.STREAM_CODEC, SeatInputPayload::input,
            ByteBufCodecs.VAR_INT, SeatInputPayload::clientTick,
            SeatInputPayload::new);

    @Override
    public CustomPacketPayload.Type<SeatInputPayload> type() {
        return TYPE;
    }
}
