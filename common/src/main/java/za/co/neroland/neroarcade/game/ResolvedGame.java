package za.co.neroland.neroarcade.game;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
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
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * A validated cartridge bound to live modules: every slot filled (empty slots resolved to their defaults),
 * every property coerced. This — never a raw {@link GameDef} — is what the runtime and the bench consume.
 *
 * <p><b>Invariants:</b> immutable; the hazard/pickup lists and their property lists have equal length
 * (0..3) in stack order; the modifier and its props are both present or both empty. Capability questions go
 * through {@link #flags}, never through module ids.
 *
 * @param unknownProperties true when the cartridge carried a property some module does not declare (ignored,
 *                          surfaced as a bench warning, dropped on the next save)
 */
public record ResolvedGame(GameDef def,
        ArenaModule arena, PropertyValues arenaProps,
        PawnModule pawn, PropertyValues pawnProps,
        RuleModule rule, PropertyValues ruleProps,
        List<HazardModule> hazards, List<PropertyValues> hazardProps,
        List<PickupModule> pickups, List<PropertyValues> pickupProps,
        ControlModule control, PropertyValues controlProps,
        PaceModule pace, PropertyValues paceProps,
        Optional<ModifierModule> modifier, Optional<PropertyValues> modifierProps,
        MultiplayerModule multiplayer, PropertyValues multiplayerProps,
        ThemeModule theme, PropertyValues themeProps,
        boolean unknownProperties) {

    public ResolvedGame {
        hazards = List.copyOf(hazards);
        hazardProps = List.copyOf(hazardProps);
        pickups = List.copyOf(pickups);
        pickupProps = List.copyOf(pickupProps);
        if (hazards.size() != hazardProps.size() || pickups.size() != pickupProps.size()
                || hazards.size() > ModuleType.HAZARD.maxStack || pickups.size() > ModuleType.PICKUP.maxStack) {
            throw new IllegalArgumentException("stack slots and their properties must align (0..3)");
        }
        if (modifier.isPresent() != modifierProps.isPresent()) {
            throw new IllegalArgumentException("modifier and its properties must both be present or both empty");
        }
    }

    /** Union of the flags of every chip in the slot (empty for an empty stack or modifier slot). */
    public Set<ModuleFlag> flags(ModuleType type) {
        EnumSet<ModuleFlag> out = EnumSet.noneOf(ModuleFlag.class);
        for (ArcadeModule module : modules(type)) {
            out.addAll(module.flags());
        }
        return Collections.unmodifiableSet(out);
    }

    /** Flags of the chip at {@code stackIndex} in the slot; empty when there is none. */
    public Set<ModuleFlag> flags(ModuleType type, int stackIndex) {
        List<? extends ArcadeModule> modules = modules(type);
        if (stackIndex < 0 || stackIndex >= modules.size()) {
            return Set.of();
        }
        Set<ModuleFlag> flags = modules.get(stackIndex).flags();
        return flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    /** The chips filling a slot, in stack order (one for single slots, 0..1 for MODIFIER, 0..3 for stacks). */
    public List<? extends ArcadeModule> modules(ModuleType type) {
        return switch (type) {
            case ARENA -> List.of(arena);
            case PAWN -> List.of(pawn);
            case RULE -> List.of(rule);
            case HAZARD -> hazards;
            case PICKUP -> pickups;
            case CONTROL -> List.of(control);
            case PACE -> List.of(pace);
            case MODIFIER -> modifier.map(List::of).orElse(List.of());
            case MULTIPLAYER -> List.of(multiplayer);
            case THEME -> List.of(theme);
        };
    }

    /** Properties of the chip at {@code stackIndex} (0 for single slots). */
    public PropertyValues props(ModuleType type, int stackIndex) {
        return switch (type) {
            case ARENA -> arenaProps;
            case PAWN -> pawnProps;
            case RULE -> ruleProps;
            case HAZARD -> hazardProps.get(stackIndex);
            case PICKUP -> pickupProps.get(stackIndex);
            case CONTROL -> controlProps;
            case PACE -> paceProps;
            case MODIFIER -> modifierProps.orElseThrow();
            case MULTIPLAYER -> multiplayerProps;
            case THEME -> themeProps;
        };
    }

    /** Arena width as the module reports it (the validator refuses oversize; the runtime clamps). */
    public int arenaWidth() {
        return arena.width(arenaProps);
    }

    public int arenaHeight() {
        return arena.height(arenaProps);
    }

    /** Seats the MULTIPLAYER chip asks for, clamped to 1..{@link Budgets#MAX_SEATS}. */
    public int seats() {
        return Math.max(1, Math.min(Budgets.MAX_SEATS, multiplayer.seats(multiplayerProps)));
    }

    /** True when the cartridge itself fills the slot (false = the bench shows the default in grey). */
    public boolean filled(ModuleType type) {
        return def.slots().containsKey(type);
    }
}
