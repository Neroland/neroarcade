package za.co.neroland.neroarcade.cabinet;

import java.util.Collection;
import java.util.UUID;

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

    /** False only when {@code cabinetsNeedPower} is on and the cabinet has no power (002 §11). */
    boolean powered();
}
