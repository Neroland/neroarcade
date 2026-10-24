package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

/**
 * Final review H1: the product space the named-combination tests do not cover. Every shipped ARENA × PAWN pair, with
 * the HAZARD and PICKUP stacks filled to three (rotating through every shipped hazard and pickup), every CONTROL,
 * every MULTIPLAYER shape at one seat and at 2–4 seats (rotating), and every property of every chip at its minimum
 * and at its maximum. RULE, PACE, MODIFIER (or none) and THEME rotate deterministically. Each cartridge that
 * validates runs up to 400 ticks of {@code randomWalk()} headless with a 20-second match cap, and must not throw and
 * must stay inside the entity and snapshot budgets. A crash found here is fixed in the chip, never in this test.
 */
class CombinationFuzzTest {

    private static final int TICKS = 400;
    /** One short match takes milliseconds; this only catches a chip that never returns. */
    private static final int RUN_TIMEOUT_SECONDS = 20;

    @BeforeEach
    void setUp() {
        TestModules.installShipped();
        Budgets.overrideForTests(Budgets.Key.MAX_MATCH_SECONDS, 20);
    }

    @AfterEach
    void tearDown() {
        Budgets.clearTestOverrides();
        TestModules.reset();
    }

    private static List<ArcadeModule> shipped(ModuleType type) {
        List<ArcadeModule> out = new ArrayList<>();
        for (ArcadeModule module : ModuleRegistry.byType(type)) {
            if (module.id().getNamespace().equals("neroarcade")) {
                out.add(module);
            }
        }
        assertTrue(!out.isEmpty(), "no shipped " + type);
        return out;
    }

    /** Every property of {@code module} at its minimum ({@code max=false}) or maximum. */
    private static SlotEntry extreme(ArcadeModule module, boolean max) {
        Map<String, PropertyValue> props = new LinkedHashMap<>();
        for (PropertySpec spec : module.properties()) {
            PropertyValue value = switch (spec.kind()) {
                case INT -> new PropertyValue.IntValue(max ? spec.max() : spec.min());
                case BOOL -> new PropertyValue.BoolValue(max);
                case ENUM -> new PropertyValue.EnumValue(spec.constants().get(max ? spec.constants().size() - 1 : 0));
            };
            props.put(spec.name(), value);
        }
        return new SlotEntry(module.id(), props);
    }

    @Test
    void everyArenaAndPawnRunsWithStackedChipsAtTheirExtremes() {
        List<ArcadeModule> arenas = shipped(ModuleType.ARENA);
        List<ArcadeModule> pawns = shipped(ModuleType.PAWN);
        List<ArcadeModule> rules = shipped(ModuleType.RULE);
        List<ArcadeModule> hazards = shipped(ModuleType.HAZARD);
        List<ArcadeModule> pickups = shipped(ModuleType.PICKUP);
        List<ArcadeModule> controls = shipped(ModuleType.CONTROL);
        List<ArcadeModule> paces = shipped(ModuleType.PACE);
        List<ArcadeModule> modifiers = shipped(ModuleType.MODIFIER);
        List<ArcadeModule> multiplayers = shipped(ModuleType.MULTIPLAYER);
        List<ArcadeModule> themes = shipped(ModuleType.THEME);

        // each match runs on a daemon thread so an endless loop in a chip fails this test instead of hanging it
        java.util.concurrent.ExecutorService watchdog = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "combination-fuzz");
            t.setDaemon(true);
            return t;
        });
        long started = System.nanoTime();
        List<String> failures = new ArrayList<>();
        int runs = 0;
        int invalid = 0;
        int k = 0;
        for (ArcadeModule arena : arenas) {
            for (ArcadeModule pawn : pawns) {
                for (boolean max : new boolean[] {false, true}) {
                    for (ArcadeModule control : controls) {
                        for (ArcadeModule multiplayer : multiplayers) {
                            for (int wantSeats : new int[] {1, 2 + (k % 3)}) {
                                k++;
                                Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
                                slots.put(ModuleType.ARENA, List.of(extreme(arena, max)));
                                slots.put(ModuleType.PAWN, List.of(extreme(pawn, max)));
                                slots.put(ModuleType.RULE, List.of(extreme(rules.get(k % rules.size()), max)));
                                List<SlotEntry> hazardStack = new ArrayList<>();
                                List<SlotEntry> pickupStack = new ArrayList<>();
                                for (int i = 0; i < 3; i++) {
                                    hazardStack.add(extreme(hazards.get((k + i) % hazards.size()), max));
                                    pickupStack.add(extreme(pickups.get((k + i) % pickups.size()), max));
                                }
                                slots.put(ModuleType.HAZARD, hazardStack);
                                slots.put(ModuleType.PICKUP, pickupStack);
                                slots.put(ModuleType.CONTROL, List.of(extreme(control, max)));
                                slots.put(ModuleType.PACE, List.of(extreme(paces.get(k % paces.size()), max)));
                                int m = k % (modifiers.size() + 1);
                                if (m < modifiers.size()) {
                                    slots.put(ModuleType.MODIFIER, List.of(extreme(modifiers.get(m), max)));
                                }
                                slots.put(ModuleType.MULTIPLAYER, List.of(extreme(multiplayer, max)));
                                slots.put(ModuleType.THEME, List.of(extreme(themes.get(k % themes.size()), max)));
                                GameDef def = TestModules.def(slots);
                                String name = arena.id().getPath() + " + " + pawn.id().getPath() + " + "
                                        + control.id().getPath() + " + " + multiplayer.id().getPath() + " seats "
                                        + wantSeats + (max ? " @max" : " @min") + " #" + k;
                                ResolvedGame game;
                                try {
                                    game = CartridgeValidator.validate(def);
                                } catch (ValidationError refused) {
                                    invalid++; // the server never runs a cartridge the validator refuses
                                    continue;
                                } catch (RuntimeException e) {
                                    failures.add(name + " [validate]: " + e);
                                    continue;
                                }
                                int seats = Math.max(1, Math.min(wantSeats, game.seats()));
                                runs++;
                                long seed = 0x5EEDL + k;
                                java.util.concurrent.Future<MatchState> future = watchdog.submit(() ->
                                        HeadlessMatch.of(game, seats, seed)
                                                .runToEnd(HeadlessMatch.InputScript.randomWalk(),
                                                        Budgets.COUNTDOWN_TICKS + TICKS)
                                                .state());
                                try {
                                    MatchState state;
                                    try {
                                        state = future.get(RUN_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (java.util.concurrent.TimeoutException hung) {
                                        // a chip that never returns hangs the server thread: stop here and name it
                                        throw new AssertionError(name + ": still running after "
                                                + RUN_TIMEOUT_SECONDS + " s (a chip loops without end)");
                                    } catch (java.util.concurrent.ExecutionException thrown) {
                                        throw thrown.getCause() instanceof RuntimeException r ? r
                                                : new IllegalStateException(thrown.getCause());
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(interrupted);
                                    }
                                    if (state.entityCount() > Budgets.maxEntities()) {
                                        failures.add(name + ": " + state.entityCount() + " entities");
                                    }
                                    int keyframe = SnapshotCodec.writeKeyframe(state).length;
                                    if (keyframe > Budgets.WORST_CASE_KEYFRAME_BYTES) {
                                        failures.add(name + ": keyframe " + keyframe + " bytes");
                                    }
                                } catch (RuntimeException e) {
                                    StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
                                    failures.add(name + ": " + e + " at " + at);
                                }
                            }
                        }
                    }
                }
            }
        }
        watchdog.shutdownNow();
        System.out.println("combination fuzz: " + runs + " matches ran, " + invalid + " refused by the validator, "
                + (System.nanoTime() - started) / 1_000_000 + " ms");
        assertTrue(failures.isEmpty(), failures.size() + " of " + runs + " runs failed, e.g. "
                + failures.subList(0, Math.min(15, failures.size())));
        assertTrue(runs >= 1000, "too few cartridges validated to mean anything: " + runs + " ran, " + invalid
                + " refused");
    }
}
