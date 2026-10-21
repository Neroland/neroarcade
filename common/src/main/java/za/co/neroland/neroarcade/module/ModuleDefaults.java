package za.co.neroland.neroarcade.module;

import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The module an empty slot resolves to.
 *
 * <p><b>Invariant:</b> defaults are applied at resolve time ({@code ResolvedGame}), never written into a
 * cartridge — a cartridge that leaves a slot empty keeps following the server's default. HAZARD, PICKUP and
 * MODIFIER have no default (no hooks run). Every other slot's default must be registered; {@link #verify()}
 * fails init with a clear log line otherwise, so a broken pack never reaches match time.
 */
public final class ModuleDefaults {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeroArcade");

    public static final Identifier ARENA = id("arena/grid_16");
    public static final Identifier PAWN = id("pawn/cursor");
    public static final Identifier RULE = id("rule/survive_time");
    public static final Identifier CONTROL = id("control/four_way");
    public static final Identifier PACE = id("pace/steady");
    public static final Identifier MULTIPLAYER = id("multiplayer/solo");
    public static final Identifier THEME = id("theme/neon_void");

    private ModuleDefaults() {
    }

    /** The empty-slot default; empty for HAZARD, PICKUP and MODIFIER. */
    public static Optional<Identifier> defaultFor(ModuleType type) {
        return Optional.ofNullable(switch (type) {
            case ARENA -> ARENA;
            case PAWN -> PAWN;
            case RULE -> RULE;
            case CONTROL -> CONTROL;
            case PACE -> PACE;
            case MULTIPLAYER -> MULTIPLAYER;
            case THEME -> THEME;
            case HAZARD, PICKUP, MODIFIER -> null;
        });
    }

    /**
     * Checks every default resolves in the registry to a module of its slot.
     *
     * @throws IllegalStateException naming the first missing default (after logging it)
     */
    public static void verify() {
        for (ModuleType type : ModuleType.values()) {
            Optional<Identifier> id = defaultFor(type);
            if (id.isPresent() && ModuleRegistry.get(id.get(), ModuleRegistry.hookOf(type)).isEmpty()) {
                String message = "NeroArcade cannot start: the default " + type.key() + " module " + id.get()
                        + " is not registered. The module pack is broken; reinstall NeroArcade.";
                LOGGER.error("[NeroArcade] {}", message);
                throw new IllegalStateException(message);
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neroarcade", path);
    }
}
