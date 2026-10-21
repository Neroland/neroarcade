package za.co.neroland.neroarcade.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.ThemeModule;

/**
 * Every module the server knows, keyed by id.
 *
 * <p><b>Invariants:</b> registration happens only during common init ({@code ArcadeModules.init()}, step 3,
 * before the creative tab); after {@link #freeze()} the registry is immutable and {@link #register} throws.
 * No runtime registration, no datapack modules, no reload — modules are Java, parameters are data. Every
 * listing is sorted by id (namespace, then path), so nothing downstream depends on registration order. An id
 * a cartridge names that is not here is the validation error {@code unknown_module} — never a silent drop,
 * never a substitution.
 */
public final class ModuleRegistry {

    /** The name part of a module path: {@code <slot>/<name>}. */
    public static final Pattern MODULE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    private static final Comparator<ArcadeModule> BY_ID = Comparator
            .comparing((ArcadeModule m) -> m.id().getNamespace())
            .thenComparing(m -> m.id().getPath());

    private static Map<Identifier, ArcadeModule> byId = new LinkedHashMap<>();
    private static Map<ModuleType, List<ArcadeModule>> byType = new EnumMap<>(ModuleType.class);
    private static List<ArcadeModule> all = List.of();
    private static boolean frozen;

    private ModuleRegistry() {
    }

    /**
     * Registers a module. Init only.
     *
     * @throws IllegalStateException after {@link #freeze()}, on a duplicate id, on a type/hook mismatch, on a
     *                               bad id path or on a bad property list
     */
    public static synchronized void register(ArcadeModule module) {
        if (frozen) {
            throw new IllegalStateException("module registry is frozen; cannot register " + module.id());
        }
        Identifier id = module.id();
        ModuleType type = module.type();
        if (byId.containsKey(id)) {
            throw new IllegalStateException("duplicate module id " + id);
        }
        String prefix = type.key() + "/";
        String path = id.getPath();
        if (!path.startsWith(prefix) || !MODULE_NAME.matcher(path.substring(prefix.length())).matches()) {
            throw new IllegalStateException("module " + id + ": path must be " + prefix + "<name>");
        }
        int hooks = 0;
        for (ModuleType candidate : ModuleType.values()) {
            if (hookOf(candidate).isInstance(module)) {
                hooks++;
                if (candidate != type) {
                    throw new IllegalStateException("module " + id + " is a " + type + " but implements "
                            + hookOf(candidate).getSimpleName());
                }
            }
        }
        if (hooks != 1) {
            throw new IllegalStateException("module " + id + " must implement exactly one hook interface ("
                    + hookOf(type).getSimpleName() + ")");
        }
        List<PropertySpec> props = module.properties();
        if (props == null || module.flags() == null) {
            throw new IllegalStateException("module " + id + ": properties and flags must not be null");
        }
        if (props.stream().map(PropertySpec::name).distinct().count() != props.size()) {
            throw new IllegalStateException("module " + id + ": duplicate property name");
        }
        byId.put(id, module);
        byType.computeIfAbsent(type, t -> new ArrayList<>()).add(module);
    }

    /** Ends registration: sorts, snapshots and locks. Idempotent. */
    public static synchronized void freeze() {
        if (frozen) {
            return;
        }
        Map<ModuleType, List<ArcadeModule>> sorted = new EnumMap<>(ModuleType.class);
        List<ArcadeModule> everything = new ArrayList<>();
        for (ModuleType type : ModuleType.values()) {
            List<ArcadeModule> list = new ArrayList<>(byType.getOrDefault(type, List.of()));
            list.sort(BY_ID);
            sorted.put(type, List.copyOf(list));
            everything.addAll(list);
        }
        everything.sort(BY_ID);
        byType = sorted;
        byId = Map.copyOf(byId);
        all = List.copyOf(everything);
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    public static synchronized Optional<ArcadeModule> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Typed lookup; empty if the id is unknown or registered under another hook. */
    public static <M extends ArcadeModule> Optional<M> get(Identifier id, Class<M> hook) {
        return get(id).filter(hook::isInstance).map(hook::cast);
    }

    /** Immutable, sorted by id — bench rack order and gallery order. */
    public static synchronized List<ArcadeModule> byType(ModuleType type) {
        List<ArcadeModule> list = byType.getOrDefault(type, List.of());
        if (frozen) {
            return list;
        }
        List<ArcadeModule> copy = new ArrayList<>(list);
        copy.sort(BY_ID);
        return List.copyOf(copy);
    }

    /** Every module, sorted by id. */
    public static synchronized List<ArcadeModule> all() {
        if (frozen) {
            return all;
        }
        List<ArcadeModule> copy = new ArrayList<>(byId.values());
        copy.sort(BY_ID);
        return List.copyOf(copy);
    }

    public static synchronized int size() {
        return byId.size();
    }

    /** The hook interface a module of this slot must implement. */
    public static Class<? extends ArcadeModule> hookOf(ModuleType type) {
        return switch (type) {
            case ARENA -> ArenaModule.class;
            case PAWN -> PawnModule.class;
            case RULE -> RuleModule.class;
            case HAZARD -> HazardModule.class;
            case PICKUP -> PickupModule.class;
            case CONTROL -> ControlModule.class;
            case PACE -> PaceModule.class;
            case MODIFIER -> ModifierModule.class;
            case MULTIPLAYER -> MultiplayerModule.class;
            case THEME -> ThemeModule.class;
        };
    }

    /** Tests only: clears and unfreezes. */
    static synchronized void resetForTests() {
        byId = new LinkedHashMap<>();
        byType = new EnumMap<>(ModuleType.class);
        all = List.of();
        frozen = false;
    }
}
