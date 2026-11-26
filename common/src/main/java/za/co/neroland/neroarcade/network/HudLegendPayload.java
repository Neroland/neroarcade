package za.co.neroland.neroarcade.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the HUD's control legend and rule sentence, sent once when the recipient takes a seat
 * (003 §11). Lang keys only: the CONTROL chip's {@code legend(props)} lines and the RULE chip's
 * {@code hudLangKey()}; {@code ruleArgs} are its {@code hudArgs(props)}. Each legend line is translated on the client
 * with two arguments: {@code %1$s} = the player's bound A key name, {@code %2$s} = the bound B key name (a line uses
 * them only when a button means something specific, e.g. "%1$s: fire").
 */
public record HudLegendPayload(BlockPos host, List<String> legendLangKeys, String ruleLangKey, int[] ruleArgs)
        implements CustomPacketPayload {

    public static final int MAX_LINES = 8;

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<HudLegendPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "hud_legend"));
    //?} else {
    /*public static final CustomPacketPayload.Type<HudLegendPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "hud_legend"), HudLegendPayload.class);
    *///?}

    //? if >=1.20.5 {
    public static final StreamCodec<ByteBuf, HudLegendPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HudLegendPayload::host,
            ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(MAX_LINES)), HudLegendPayload::legendLangKeys,
            ByteBufCodecs.stringUtf8(128), HudLegendPayload::ruleLangKey,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)).map(
                    list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> java.util.Arrays.stream(array).boxed().toList()), HudLegendPayload::ruleArgs,
            HudLegendPayload::new);
    //?} else {
    /*public static final StreamCodec<FriendlyByteBuf, HudLegendPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.host());
                buf.writeVarInt(payload.legendLangKeys().size());
                for (String key : payload.legendLangKeys()) {
                    buf.writeUtf(key, 128);
                }
                buf.writeUtf(payload.ruleLangKey(), 128);
                buf.writeVarInt(payload.ruleArgs().length);
                for (int argument : payload.ruleArgs()) {
                    buf.writeVarInt(argument);
                }
            }, buf -> {
                BlockPos host = buf.readBlockPos();
                int legendLength = buf.readVarInt();
                if (legendLength < 0 || legendLength > MAX_LINES) {
                    throw new io.netty.handler.codec.DecoderException("Too many arcade legend lines");
                }
                List<String> keys = new java.util.ArrayList<>(legendLength);
                for (int index = 0; index < legendLength; index++) {
                    keys.add(buf.readUtf(128));
                }
                String rule = buf.readUtf(128);
                int argumentCount = buf.readVarInt();
                if (argumentCount < 0 || argumentCount > 8) {
                    throw new io.netty.handler.codec.DecoderException("Too many arcade rule arguments");
                }
                int[] arguments = new int[argumentCount];
                for (int index = 0; index < argumentCount; index++) {
                    arguments[index] = buf.readVarInt();
                }
                return new HudLegendPayload(host, keys, rule, arguments);
            });
    *///?}

    public HudLegendPayload {
        legendLangKeys = List.copyOf(legendLangKeys.size() > MAX_LINES ? legendLangKeys.subList(0, MAX_LINES)
                : legendLangKeys);
        ruleArgs = ruleArgs == null ? new int[0] : ruleArgs.clone();
    }

    @Override
    public int[] ruleArgs() {
        return ruleArgs.clone();
    }

    @Override
    public CustomPacketPayload.Type<HudLegendPayload> type() {
        return TYPE;
    }
}
