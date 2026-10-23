package za.co.neroland.neroarcade.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: one action at the Arcade Exchange the sender has open (003 §8.2, §11). Accepted only while the
 * sender's open menu is an {@code ExchangeMenu}; at most 4 per second per player, the excess ignored. Every action is
 * re-checked on the server (kill switch, rights, caps, the tray) and refused with a plain sentence.
 *
 * <ul>
 *   <li>{@link Op#PAGE}: browse page {@code page} of the listings. {@link Op#DRAFTS}: page {@code page} of the
 *       sender's own drafts.</li>
 *   <li>{@link Op#SELECT}: the listing the sender looks at (resends the page; selection is otherwise client-side).</li>
 *   <li>{@link Op#PUBLISH}: the written cartridge in the kiosk tray (or, with the tray empty, the main hand).</li>
 *   <li>{@link Op#UNPUBLISH}, {@link Op#DOWNLOAD}, {@link Op#HEART}: listing {@code listingId}.</li>
 *   <li>{@link Op#FETCH_DRAFT}: the sender's draft at index {@code listingId} onto the Blank Cartridge in the tray.</li>
 * </ul>
 */
public record ExchangeActionPayload(Op op, int listingId, int page) implements CustomPacketPayload {

    public enum Op {
        PAGE,
        SELECT,
        PUBLISH,
        UNPUBLISH,
        DOWNLOAD,
        HEART,
        FETCH_DRAFT,
        DRAFTS
    }

    public static final CustomPacketPayload.Type<ExchangeActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "exchange_action"));

    private static final StreamCodec<ByteBuf, Op> OP_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal);

    public static final StreamCodec<ByteBuf, ExchangeActionPayload> STREAM_CODEC = StreamCodec.composite(
            OP_CODEC, ExchangeActionPayload::op,
            ByteBufCodecs.VAR_INT, ExchangeActionPayload::listingId,
            ByteBufCodecs.VAR_INT, ExchangeActionPayload::page,
            ExchangeActionPayload::new);

    @Override
    public CustomPacketPayload.Type<ExchangeActionPayload> type() {
        return TYPE;
    }
}
