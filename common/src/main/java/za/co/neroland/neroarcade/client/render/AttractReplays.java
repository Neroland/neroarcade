package za.co.neroland.neroarcade.client.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.screen.AttractPlayer;
import za.co.neroland.neroarcade.runtime.Replay;
import za.co.neroland.neroarcade.runtime.ShippedReplays;

/**
 * The running attract replays, one {@link AttractPlayer} per cartridge id that has a shipped replay and a screen
 * that drew it in the last {@value #IDLE_TICKS} ticks. Screens ask with {@link #player}; the client tick advances
 * every live player once ({@link #tick}) and drops the idle ones, so a cartridge nobody is looking at costs
 * nothing. At most {@value #MAX_PLAYERS} run at once (there are sixteen shipped replays). Render/client thread
 * only. CLIENT-ONLY.
 */
public final class AttractReplays {

    public static final int IDLE_TICKS = 40;
    public static final int MAX_PLAYERS = 16;

    private record Live(AttractPlayer player, long[] lastUsed) {
    }

    private static final Map<String, Live> LIVE = new HashMap<>();
    /** Cartridge ids known to have no usable replay (checked once per session). */
    private static final Map<String, Boolean> NONE = new HashMap<>();

    private AttractReplays() {
    }

    /** The shared player for this cartridge id, or null when it has no shipped replay. */
    public static @Nullable AttractPlayer player(String cartridgeId, long now) {
        if (cartridgeId == null || cartridgeId.isEmpty() || NONE.containsKey(cartridgeId)) {
            return null;
        }
        Live live = LIVE.get(cartridgeId);
        if (live == null) {
            if (LIVE.size() >= MAX_PLAYERS) {
                return null;
            }
            Optional<Replay> replay = ShippedReplays.forCartridge(cartridgeId);
            AttractPlayer player = replay.map(AttractPlayer::of).orElse(null);
            if (player == null) {
                NONE.put(cartridgeId, Boolean.TRUE);
                return null;
            }
            live = new Live(player, new long[] {now});
            LIVE.put(cartridgeId, live);
        }
        live.lastUsed()[0] = now;
        return live.player();
    }

    /** One client tick: advance every player drawn recently, drop the rest. */
    public static void tick(long now) {
        Iterator<Live> it = LIVE.values().iterator();
        while (it.hasNext()) {
            Live live = it.next();
            if (now - live.lastUsed()[0] > IDLE_TICKS) {
                it.remove();
            } else {
                live.player().advance();
            }
        }
    }

    /** Players running now (the budget readout). */
    public static int live() {
        return LIVE.size();
    }

    public static void clear() {
        LIVE.clear();
        NONE.clear();
    }
}
