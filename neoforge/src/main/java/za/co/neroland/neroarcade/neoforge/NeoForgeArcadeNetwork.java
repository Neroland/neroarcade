package za.co.neroland.neroarcade.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.platform.NetworkPlatform;

/**
 * NeoForge side of the networking seam: registers every {@link ArcadeNetwork} payload on NeroArcade'
 * own registrar during {@code RegisterPayloadHandlersEvent} and implements the send methods.
 * Registered as the {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>The registrar is {@code optional()}, so a vanilla (or NeroArcade-less) client can still connect —
 * it simply never receives a NeroArcade payload. Handlers run through {@code context.enqueueWork}, on
 * the game thread.
 */
public final class NeoForgeArcadeNetwork implements NetworkPlatform {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeArcadeNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ArcadeNetwork.PROTOCOL_VERSION).optional();
        for (ArcadeNetwork.Clientbound<?> cb : ArcadeNetwork.clientbound()) {
            registerClientbound(registrar, cb);
        }
        for (ArcadeNetwork.Serverbound<?> sb : ArcadeNetwork.serverbound()) {
            registerServerbound(registrar, sb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, ArcadeNetwork.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadRegistrar registrar, ArcadeNetwork.Serverbound<T> sb) {
        registrar.playToServer(sb.type(), sb.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        sb.handler().accept(payload, serverPlayer);
                    }
                }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.6 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }
}
