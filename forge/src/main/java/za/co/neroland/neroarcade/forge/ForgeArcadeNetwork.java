package za.co.neroland.neroarcade.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.platform.NetworkPlatform;

/**
 * Forge side of the cross-loader packet seam, on NeroArcade' own {@code neroarcade:main} channel.
 * Registered as the {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>Forge seals a channel at {@code build()}, so every payload must be declared before then — which
 * is why NeroArcade owns this channel rather than adding to Neroland Core's, and why
 * {@link #register()} must run <b>after</b> {@code NeroArcadeCommon.init()}, where the payloads are
 * declared. {@code optional()} keeps a NeroArcade-less client connectable. While no payload is
 * declared the channel is not built at all and the send methods are no-ops.
 */
public final class ForgeArcadeNetwork implements NetworkPlatform {

    private static Channel<CustomPacketPayload> channel;

    public static void register() {
        if (!ArcadeNetwork.hasPayloads()) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] no payloads declared; neroarcade:main channel not built");
            return;
        }
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, ArcadeNetwork.CHANNEL))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (ArcadeNetwork.Clientbound<?> cb : ArcadeNetwork.clientbound()) {
            registerClientbound(play, cb);
        }
        for (ArcadeNetwork.Serverbound<?> sb : ArcadeNetwork.serverbound()) {
            registerServerbound(play, sb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            ArcadeNetwork.Clientbound<T> cb) {
        play.addMain(cb.type(), registryCodec(cb.codec()),
                (payload, context) -> cb.handler().accept(payload));
    }

    /** Both directions share the one bidirectional flow; the sender is what distinguishes them. */
    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            ArcadeNetwork.Serverbound<T> sb) {
        play.addMain(sb.type(), registryCodec(sb.codec()), (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer serverPlayer) {
                sb.handler().accept(payload, serverPlayer);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.SERVER.noArg());
        }
    }
}
