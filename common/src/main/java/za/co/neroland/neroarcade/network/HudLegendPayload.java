package za.co.neroland.neroarcade.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
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

    public static final CustomPacketPayload.Type<HudLegendPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "hud_legend"));

    public static final StreamCodec<ByteBuf, HudLegendPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HudLegendPayload::host,
            ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(MAX_LINES)), HudLegendPayload::legendLangKeys,
            ByteBufCodecs.stringUtf8(128), HudLegendPayload::ruleLangKey,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)).map(
                    list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> java.util.Arrays.stream(array).boxed().toList()), HudLegendPayload::ruleArgs,
            HudLegendPayload::new);

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
