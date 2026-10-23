package za.co.neroland.neroarcade.compat;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.compat.colonies.MoraleSeam;
import za.co.neroland.neroarcade.compat.economy.CoinOpSeam;
import za.co.neroland.neroarcade.compat.palette.PaletteSeam;
import za.co.neroland.neroarcade.compat.soundtrack.SoundtrackSeam;
import za.co.neroland.neroarcade.compat.tournament.TournamentSeam;
import za.co.neroland.neroarcade.telemetry.NeroArcadeTelemetry;

/**
 * The soft seams (003 §10), resolved <b>once</b> in common init step 5 and never per interaction. The family façade
 * pattern: each seam is an interface in {@code compat/<x>} with a {@code NO_OP}; an implementation that imports a
 * sibling lives under {@code compat/<x>/bridge}, is listed in {@code META-INF/services/<seam interface>}, and is
 * looked up through {@link ServiceLoader} <b>only if</b> that sibling is loaded — inside
 * {@code catch (RuntimeException | LinkageError)}, so a missing or changed sibling can never take NeroArcade down.
 *
 * <table>
 *   <caption>0.1.0</caption>
 *   <tr><td>{@link CoinOpSeam}</td><td>NeroEconomy</td><td>real bridge, config off by default</td></tr>
 *   <tr><td>{@link TournamentSeam}</td><td>(no sibling API yet)</td><td>no-op</td></tr>
 *   <tr><td>{@link SoundtrackSeam}</td><td>NeroNotes</td><td>no-op (no bridge shipped)</td></tr>
 *   <tr><td>{@link MoraleSeam}</td><td>NeroColonies</td><td>no-op (no bridge shipped)</td></tr>
 *   <tr><td>{@link PaletteSeam}</td><td>Neroland Core</td><td>real, Core only</td></tr>
 * </table>
 *
 * Logs carry the mod id and the exception class only.
 */
public final class Seams {

    private static volatile CoinOpSeam coinOp = CoinOpSeam.NO_OP;
    private static volatile TournamentSeam tournament = TournamentSeam.NO_OP;
    private static volatile SoundtrackSeam soundtrack = SoundtrackSeam.NO_OP;
    private static volatile MoraleSeam morale = MoraleSeam.NO_OP;
    private static volatile PaletteSeam palette = PaletteSeam.NO_OP;
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static boolean initialised;

    private Seams() {
    }

    /** Common init step 5, once. */
    public static synchronized void init(ModPresence presence) {
        if (initialised) {
            return;
        }
        initialised = true;
        resolve(presence);
        try {
            PaletteSeam.registerFinishes();
            palette = PaletteSeam.CORE;
        } catch (RuntimeException | LinkageError e) {
            failed("palette", e);
        }
    }

    /** Picks every seam for {@code presence} (tests call this directly; {@link #reset()} first). */
    public static synchronized void resolve(ModPresence presence) {
        ModPresence p = presence == null ? ModPresence.NONE : presence;
        coinOp = load(p, CoinOpSeam.MOD_ID, CoinOpSeam.class, CoinOpSeam.NO_OP, "coin-op play and paid downloads");
        soundtrack = load(p, SoundtrackSeam.MOD_ID, SoundtrackSeam.class, SoundtrackSeam.NO_OP, "theme soundtracks");
        morale = load(p, MoraleSeam.MOD_ID, MoraleSeam.class, MoraleSeam.NO_OP, "colony morale");
        tournament = TournamentSeam.NO_OP; // no sibling publishes a tournament API in 0.1.0
    }

    private static <S> S load(ModPresence presence, String modId, Class<S> type, S noOp, String feature) {
        try {
            if (!presence.isLoaded(modId)) {
                return noOp;
            }
            S found = ServiceLoader.load(type, type.getClassLoader()).findFirst().orElse(null);
            if (found == null) {
                NeroArcadeCommon.LOGGER.info("[NeroArcade] {} present but this jar carries no bridge for it; {} off",
                        modId, feature);
                return noOp;
            }
            NeroArcadeCommon.LOGGER.info("[NeroArcade] {} found; {} available", modId, feature);
            return found;
        } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
            failed(modId, e);
            return noOp;
        }
    }

    /** One WARN and one telemetry report per sibling per session; the seam's caller falls back. */
    public static void failed(String modId, Throwable e) {
        if (FAILED.add(modId)) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] The {} integration failed ({}); it is off for this session.",
                    modId, e.getClass().getName());
            NeroArcadeTelemetry.captureHandledException(e, "compat:" + modId, "bridge");
        }
    }

    public static CoinOpSeam coinOp() {
        return coinOp;
    }

    public static TournamentSeam tournament() {
        return tournament;
    }

    public static SoundtrackSeam soundtrack() {
        return soundtrack;
    }

    public static MoraleSeam morale() {
        return morale;
    }

    public static PaletteSeam palette() {
        return palette;
    }

    /** Whether a real (non-no-op) coin-op bridge is installed. */
    public static boolean coinOpBridged() {
        return coinOp != CoinOpSeam.NO_OP;
    }

    /** Tests only: every seam back to its no-op. */
    public static synchronized void reset() {
        coinOp = CoinOpSeam.NO_OP;
        tournament = TournamentSeam.NO_OP;
        soundtrack = SoundtrackSeam.NO_OP;
        morale = MoraleSeam.NO_OP;
        palette = PaletteSeam.NO_OP;
        FAILED.clear();
        initialised = false;
    }
}
