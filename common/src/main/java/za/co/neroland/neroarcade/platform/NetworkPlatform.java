package za.co.neroland.neroarcade.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-loader packet-send seam (counterpart to NeoForge {@code PacketDistributor} / Forge
 * {@code Channel} / Fabric {@code Server|ClientPlayNetworking.send}). Payload <em>types</em> and
 * their handlers are declared once in {@link za.co.neroland.neroarcade.network.ArcadeNetwork}; each
 * loader module registers them on NeroArcade' own channel ({@code neroarcade:main}) and implements
 * this send interface, resolved via {@link Services#NETWORK}.
 *
 * <h2>Why NeroArcade ships its own seam rather than reusing Neroland Core's</h2>
 *
 * <p>Core's network seam would happily carry a NeroArcade payload, but payload
 * <em>registration</em> is Core-internal: Core drains its payload lists during its own bootstrap (on
 * Forge the channel is {@code build()}-sealed inside Core's constructor, on Fabric the types are
 * registered from Core's {@code onInitialize}), so anything a downstream mod adds afterwards is
 * silently dropped. On NeoForge it would land on Core's registrar and protocol version, coupling
 * NeroArcade' wire format to Core's. NeroArcade therefore never touches Core's network registry.
 */
public interface NetworkPlatform {

    /** Server &rarr; one client. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client &rarr; server (call only on the physical client). */
    void sendToServer(CustomPacketPayload payload);
}
