package za.co.neroland.neroarcade.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.platform.NetworkPlatform;

/**
 * Fabric side of the networking seam (the {@code neroarcade:main} payloads). {@link #registerCommon()}
 * (mod init, both sides) registers the payload types and the serverbound receivers;
 * {@link #registerClient()} (client init) registers the clientbound receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server. Registered as the {@link NetworkPlatform}
 * implementation via {@code META-INF/services}.
 *
 * <p>Receivers hop to the client / server thread before running a handler, which is what makes
 * plain-data mirror caches and saved-data access safe without locking.
 */
public final class FabricArcadeNetwork implements NetworkPlatform {

    /** Mod-init (both sides): clientbound payload types, plus serverbound types and receivers. */
    public static void registerCommon() {
        for (ArcadeNetwork.Clientbound<?> cb : ArcadeNetwork.clientbound()) {
            registerClientboundType(cb);
        }
        for (ArcadeNetwork.Serverbound<?> sb : ArcadeNetwork.serverbound()) {
            registerServerbound(sb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (ArcadeNetwork.Clientbound<?> cb : ArcadeNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            ArcadeNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            ArcadeNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            ArcadeNetwork.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
