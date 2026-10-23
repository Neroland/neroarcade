package za.co.neroland.neroarcade.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.bench.BenchActions;
import za.co.neroland.neroarcade.cabinet.SeatPackets;
import za.co.neroland.neroarcade.exchange.ExchangeActions;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.runtime.net.SeatInputPayload;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/**
 * The cross-loader payload registry for NeroArcade' own channel, {@code neroarcade:main}. Payloads
 * are declared here once (type + stream codec + handler); each loader module iterates the lists and
 * wires them to its own networking API — NeoForge's {@code PayloadRegistrar}, Forge's
 * {@code ChannelBuilder}, Fabric's {@code PayloadTypeRegistry} + {@code Client/ServerPlayNetworking}.
 * Sending goes through the {@link Services#NETWORK} seam.
 *
 * <p>Ten payloads: {@code seat_input}, {@code leave_seat}, {@code bench_edit}, {@code exchange_action} (to the server)
 * and {@code snapshot}, {@code match_phase}, {@code hud_legend}, {@code bench_state}, {@code board_page},
 * {@code exchange_page} (to the client,
 * forwarded to {@link ArcadeClientSink}). Adding one is a one-line {@link #clientbound}/{@link #serverbound} call in
 * {@link #init()}. NeroArcade never adds to Neroland
 * Core's network registry (see
 * {@link za.co.neroland.neroarcade.platform.NetworkPlatform} for why).
 *
 * <p>NeroArcade is server-authoritative: anything a client sends is a request the server validates,
 * never a fact it trusts. Payloads must carry no more personal data than the receiving screen needs
 * (POPIA/GDPR data minimisation).
 */
public final class ArcadeNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client &rarr; server payload plus the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    /** The channel path under the {@code neroarcade} namespace ({@code neroarcade:main}). */
    public static final String CHANNEL = "main";

    /** Wire protocol version (NeoForge registrar version). Bump on any incompatible payload change. */
    public static final String PROTOCOL_VERSION = "1";

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private static boolean declared;

    private ArcadeNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return Collections.unmodifiableList(CLIENTBOUND);
    }

    /** Every declared client &rarr; server payload, for each loader's registration pass. */
    public static List<Serverbound<?>> serverbound() {
        return Collections.unmodifiableList(SERVERBOUND);
    }

    /** True when at least one payload has been declared (Forge only builds its channel then). */
    public static boolean hasPayloads() {
        return !CLIENTBOUND.isEmpty() || !SERVERBOUND.isEmpty();
    }

    /**
     * Declares the payloads. Called once from common init, before any loader registers them (each
     * loader entry point runs common init first, then its own network registration). On Forge the
     * channel is sealed at {@code build()}, so a payload declared later would never exist.
     */
    public static void init() {
        if (declared) {
            return; // defensive: a second call must not duplicate registrations
        }
        declared = true;
        // Client -> server: requests the server validates (sender = the connection's player, never the payload).
        serverbound(SeatInputPayload.TYPE, SeatInputPayload.STREAM_CODEC, SeatPackets::onInput);
        serverbound(LeaveSeatPayload.TYPE, LeaveSeatPayload.STREAM_CODEC, SeatPackets::onLeave);
        serverbound(BenchEditPayload.TYPE, BenchEditPayload.STREAM_CODEC, BenchActions::handle);
        // Server -> client: forwarded to the client sink the client module installs (no-op until then).
        clientbound(SnapshotPayload.TYPE, SnapshotPayload.STREAM_CODEC, p -> ArcadeClientSink.get().onSnapshot(p));
        clientbound(MatchPhasePayload.TYPE, MatchPhasePayload.STREAM_CODEC, p -> ArcadeClientSink.get().onMatchPhase(p));
        clientbound(HudLegendPayload.TYPE, HudLegendPayload.STREAM_CODEC, p -> ArcadeClientSink.get().onHudLegend(p));
        clientbound(BenchStatePayload.TYPE, BenchStatePayload.STREAM_CODEC, p -> ArcadeClientSink.get().onBenchState(p));
        // Stage 4A: a cartridge's leaderboard for the marquee and the attract screen.
        clientbound(BoardPagePayload.TYPE, BoardPagePayload.STREAM_CODEC, p -> ArcadeClientSink.get().onBoardPage(p));
        // Stage 4B: the Arcade Exchange (pages are metadata only; actions are re-checked server side).
        serverbound(ExchangeActionPayload.TYPE, ExchangeActionPayload.STREAM_CODEC, ExchangeActions::handle);
        clientbound(ExchangePagePayload.TYPE, ExchangePagePayload.STREAM_CODEC,
                p -> ArcadeClientSink.get().onExchangePage(p));
    }

    /**
     * Drops every client-side mirror, so one session's state can never bleed into the next. Each loader
     * wires this to its client-disconnect event.
     */
    public static void clearClientCaches() {
        ArcadeClientSink.get().clear();
    }
}
