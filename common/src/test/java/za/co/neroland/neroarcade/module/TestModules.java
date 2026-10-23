package za.co.neroland.neroarcade.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ControlModule;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.PaceModule;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.module.hook.ThemeModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.module.pack.arena.Grid16Arena;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;
import za.co.neroland.neroarcade.module.pack.multiplayer.SoloMultiplayer;
import za.co.neroland.neroarcade.module.pack.pace.SteadyPace;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.module.pack.theme.NeonVoidTheme;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** Registry setup, cartridge builders and flag-carrying stub chips shared by the tests. */
public final class TestModules {

    public static final String ID = "abcdefghijklmnop";
    public static final UUID AUTHOR = UUID.fromString("0f6c1d2e-3a4b-4c5d-8e9f-a0b1c2d3e4f5");

    private TestModules() {
    }

    /** Every shipped module (defaults + the Stage 3 packs) + test chips + {@code extra}, frozen. */
    public static void install(ArcadeModule... extra) {
        ModuleRegistry.resetForTests();
        ArcadeModules.registerDefaults();
        ArcadeModules.registerPacks();
        TestChips.all().forEach(ModuleRegistry::register);
        for (ArcadeModule module : extra) {
            ModuleRegistry.register(module);
        }
        ModuleRegistry.freeze();
    }

    /** Only what the mod itself ships, frozen and verified (as at init). */
    public static void installShipped() {
        ModuleRegistry.resetForTests();
        ArcadeModules.init();
    }

    public static void reset() {
        ModuleRegistry.resetForTests();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neroarcade", path);
    }

    public static GameDef def(Map<ModuleType, List<SlotEntry>> slots) {
        return new GameDef(1, ID, "Test Game", Optional.empty(), Optional.empty(), List.of(), Optional.empty(), slots,
                0L, 1);
    }

    /** All-default cartridge (every slot empty). */
    public static GameDef emptyDef() {
        return def(Map.of());
    }

    /**
     * A resolved game built directly from module instances (no registry): each given module fills its slot
     * (stack slots in argument order), every other slot is the shipped default, every property at default.
     */
    public static ResolvedGame resolvedOf(ArcadeModule... modules) {
        return resolvedOf(false, modules);
    }

    public static ResolvedGame resolvedOf(boolean unknownProperties, ArcadeModule... modules) {
        ArenaModule arena = Grid16Arena.INSTANCE;
        PawnModule pawn = CursorPawn.INSTANCE;
        RuleModule rule = SurviveTimeRule.INSTANCE;
        ControlModule control = FourWayControl.INSTANCE;
        PaceModule pace = SteadyPace.INSTANCE;
        ModifierModule modifier = null;
        MultiplayerModule multiplayer = SoloMultiplayer.INSTANCE;
        ThemeModule theme = NeonVoidTheme.INSTANCE;
        List<HazardModule> hazards = new ArrayList<>();
        List<PickupModule> pickups = new ArrayList<>();
        for (ArcadeModule m : modules) {
            switch (m) {
                case ArenaModule a -> arena = a;
                case PawnModule p -> pawn = p;
                case RuleModule r -> rule = r;
                case HazardModule h -> hazards.add(h);
                case PickupModule p -> pickups.add(p);
                case ControlModule c -> control = c;
                case PaceModule p -> pace = p;
                case ModifierModule mo -> modifier = mo;
                case MultiplayerModule mp -> multiplayer = mp;
                case ThemeModule t -> theme = t;
                default -> throw new IllegalArgumentException("not a hook module: " + m);
            }
        }
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        for (ArcadeModule m : modules) {
            slots.computeIfAbsent(m.type(), t -> new ArrayList<>()).add(SlotEntry.of(m.id()));
        }
        return new ResolvedGame(def(slots),
                arena, PropertyValues.defaults(arena),
                pawn, PropertyValues.defaults(pawn),
                rule, PropertyValues.defaults(rule),
                hazards, hazards.stream().map(PropertyValues::defaults).toList(),
                pickups, pickups.stream().map(PropertyValues::defaults).toList(),
                control, PropertyValues.defaults(control),
                pace, PropertyValues.defaults(pace),
                Optional.ofNullable(modifier), Optional.ofNullable(modifier).map(PropertyValues::defaults),
                multiplayer, PropertyValues.defaults(multiplayer),
                theme, PropertyValues.defaults(theme),
                unknownProperties);
    }

    // ---- flag-carrying stubs: shipped/test behaviour with different declared flags -------------------

    public static PawnModule pawn(ModuleFlag... flags) {
        return new PawnModule() {
            @Override
            public Identifier id() {
                return TestModules.id("pawn/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return CursorPawn.INSTANCE.properties();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
                CursorPawn.INSTANCE.spawn(ctx, seat, cellX, cellY);
            }

            @Override
            public void applyInput(ModuleContext ctx, int seat, Intent intent) {
                CursorPawn.INSTANCE.applyInput(ctx, seat, intent);
            }

            @Override
            public void tick(ModuleContext ctx, int seat) {
                CursorPawn.INSTANCE.tick(ctx, seat);
            }
        };
    }

    public static ControlModule control(ModuleFlag... flags) {
        return new ControlModule() {
            @Override
            public Identifier id() {
                return TestModules.id("control/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return FourWayControl.INSTANCE.properties();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public Intent interpret(ModuleContext ctx, int seat, SeatInput now, SeatInput previous) {
                return FourWayControl.INSTANCE.interpret(ctx, seat, now, previous);
            }

            @Override
            public List<String> legend(PropertyValues props) {
                return FourWayControl.INSTANCE.legend(props);
            }
        };
    }

    /** A rule that sets lives and never decides (only the hard stop or lives end it). */
    public static RuleModule rule(ModuleFlag... flags) {
        return new RuleModule() {
            @Override
            public Identifier id() {
                return TestModules.id("rule/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
            }

            @Override
            public RuleOutcome evaluate(ModuleContext ctx) {
                return RuleOutcome.CONTINUE;
            }
        };
    }

    public static PickupModule pickup(ModuleFlag... flags) {
        return new PickupModule() {
            @Override
            public Identifier id() {
                return TestModules.id("pickup/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return TestChips.Orb.INSTANCE.properties();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
                TestChips.Orb.INSTANCE.onMatchStart(ctx);
            }

            @Override
            public void tick(ModuleContext ctx) {
                TestChips.Orb.INSTANCE.tick(ctx);
            }

            @Override
            public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
                TestChips.Orb.INSTANCE.onCollect(ctx, seat, pickupEntityId);
            }
        };
    }

    public static HazardModule hazard(ModuleFlag... flags) {
        return new HazardModule() {
            @Override
            public Identifier id() {
                return TestModules.id("hazard/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return TestChips.Bouncer.INSTANCE.properties();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public void onMatchStart(ModuleContext ctx) {
                TestChips.Bouncer.INSTANCE.onMatchStart(ctx);
            }

            @Override
            public void tick(ModuleContext ctx) {
                TestChips.Bouncer.INSTANCE.tick(ctx);
            }
        };
    }

    public static MultiplayerModule multiplayer(boolean opponent, ModuleFlag... flags) {
        return new MultiplayerModule() {
            @Override
            public Identifier id() {
                return TestModules.id("multiplayer/stub");
            }

            @Override
            public List<PropertySpec> properties() {
                return List.of();
            }

            @Override
            public Set<ModuleFlag> flags() {
                return Set.of(flags);
            }

            @Override
            public int seats(PropertyValues props) {
                return 2;
            }

            @Override
            public SeatLayout layout(ModuleContext ctx) {
                return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
            }

            @Override
            public boolean sharesLives(PropertyValues props) {
                return false;
            }

            @Override
            public boolean runtimeOpponent(PropertyValues props) {
                return opponent;
            }

            @Override
            public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
                return fromRule;
            }
        };
    }
}
