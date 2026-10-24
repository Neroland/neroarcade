package za.co.neroland.neroarcade.cabinet;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a {@link MatchHost} needs from the world, as an interface so the host runs in a plain JVM test.
 * {@link LevelHostEnv} is the live implementation.
 */
public interface HostEnv {

    /** Server game time (rate windows, seat timestamps). */
    long gameTime();

    /** A fresh match seed (the only {@code RandomSource} use outside {@code CartridgeIds.mint}). */
    long newSeed();

    /** Players who should receive this host's screen: everyone within {@code screenRenderRange}. */
    Collection<UUID> viewers();

    /** Sends one payload to one player (dropped silently if they are gone). */
    void send(UUID player, CustomPacketPayload payload);

    /**
     * False when the cabinet has no power (002 §11). Always true in 0.1.0, which has no energy buffer; the NO POWER
     * pause and abandon logic is kept for a later release.
     */
    boolean powered();

    /** Sends one player a plain chat line by translation key (dropped silently if they are gone). No-op by default. */
    default void message(UUID player, String langKey) {
    }

    /**
     * The host unbound a seat itself (the bus shrank under it): tell the {@link SeatHolder} at {@code at} so its
     * seat field and the {@link SeatRegistry} claim go too (review 003b F7). No-op by default.
     */
    default void unseat(BlockPos at, DropReason why) {
    }
}
