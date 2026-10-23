package za.co.neroland.neroarcade.module.pack.hazard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;
import za.co.neroland.neroarcade.runtime.SeatState;

/** HAZARD pack registration, stacking by tag and the shared budget. Also hosts {@link Kit}, the WP-C test helpers. */
class HazardPackTest {

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void registersFiveChipsAlphabetically() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        HazardPack.registerAll();
        ModuleRegistry.freeze();
        List<String> ids = ModuleRegistry.byType(ModuleType.HAZARD).stream().map(m -> m.id().toString()).toList();
        assertEquals(List.of("neroarcade:hazard/bouncing_ball", "neroarcade:hazard/chaser",
                "neroarcade:hazard/falling_blocks", "neroarcade:hazard/laser_sweep",
                "neroarcade:hazard/shrinking_floor"), ids);
        for (ArcadeModule module : ModuleRegistry.byType(ModuleType.HAZARD)) {
            assertTrue(module.flags().contains(ModuleFlag.LETHAL), module.id() + " must be LETHAL");
            assertTrue(module.properties().size() <= 3, module.id().toString());
        }
        assertTrue(BouncingBallHazard.INSTANCE.flags().contains(ModuleFlag.SCORES));
    }

    @Test
    void stackOfThreeRoutesEveryEntityByTag() {
        ResolvedGame game = TestModules.resolvedOf(FallingBlocksHazard.INSTANCE, ChaserHazard.INSTANCE,
                LaserSweepHazard.INSTANCE);
        HeadlessMatch match = Kit.playing(game, 1, 11L);
        MatchState s = match.state();
        s.setStatus(0, SeatState.SHIELD, SeatState.MAX_TICKS);
        assertEquals(16, Kit.count(s, EntityKind.HAZARD, 2), "one beam entity per cell of a 16-wide row");
        assertEquals(1, Kit.count(s, EntityKind.HAZARD, 1), "one chaser");
        for (int t = 0; t < 300 && match.result().isEmpty(); t++) {
            match.step();
            for (int i = 0; i < s.entityCount(); i++) {
                int id = s.entityIdAt(i);
                if (s.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal()) {
                    continue;
                }
                int tag = s.tag(id);
                assertTrue(tag >= 0 && tag <= 2);
                if (tag == 0) {
                    assertTrue(s.get(id, Entities.VY) > 0, "a crate falls");
                }
                if (tag == 1) {
                    assertTrue(s.get(id, Entities.HP) <= ChaserHazard.HP);
                }
            }
            assertEquals(16, Kit.count(s, EntityKind.HAZARD, 2));
            assertTrue(Kit.count(s, EntityKind.HAZARD, 1) <= 1);
        }
    }

    @Test
    void twoStacksOfTheSameChipKeepSeparateCounts() {
        ResolvedGame game = Kit.with(TestModules.resolvedOf(ChaserHazard.INSTANCE, ChaserHazard.INSTANCE),
                ChaserHazard.INSTANCE, "count", 2);
        HeadlessMatch match = Kit.playing(game, 1, 5L);
        assertEquals(2, Kit.count(match.state(), EntityKind.HAZARD, 0));
        assertEquals(2, Kit.count(match.state(), EntityKind.HAZARD, 1));
    }

    @Test
    void worstStackFitsTheEntityBudget() {
        ResolvedGame game = Kit.with(Kit.with(TestModules.resolvedOf(TestChips.SizedArena.INSTANCE,
                LaserSweepHazard.INSTANCE, LaserSweepHazard.INSTANCE, LaserSweepHazard.INSTANCE),
                TestChips.SizedArena.INSTANCE, "side", 32), LaserSweepHazard.INSTANCE, "axis", "both");
        HeadlessMatch match = assertDoesNotThrow(() -> Kit.playing(game, 1, 3L));
        assertDoesNotThrow(() -> match.run(200, HeadlessMatch.InputScript.randomWalk()));
        MatchState s = match.state();
        assertTrue(s.entityCount() <= s.entityCapacity());
        assertTrue(s.entityCount() <= Budgets.HARD_MAX_ENTITIES);
    }

    /** WP-C test helpers (package-private; the pickup package has its own copy). */
    static final class Kit {

        private Kit() {
        }

        static int at(int cell) {
            return (cell << MatchState.FP_SHIFT) + MatchState.FP / 2;
        }

        /** A started match run through its countdown, so the next step simulates tick 0. */
        static HeadlessMatch playing(ResolvedGame game, int seats, long seed) {
            return HeadlessMatch.of(game, seats, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        }

        /** The hook context the runtime would pass the chip at {@code stackIndex}. */
        static ModuleContext ctx(MatchState s, ResolvedGame game, int stackIndex) {
            return new ModuleContext(s, game.hazardProps().get(stackIndex), Rng.view(s), game, s.tick(), stackIndex);
        }

        static int count(MatchState s, EntityKind kind, int tag) {
            return ids(s, kind, tag).size();
        }

        static List<Integer> ids(MatchState s, EntityKind kind, int tag) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < s.entityCount(); i++) {
                int id = s.entityIdAt(i);
                if (s.get(id, Entities.KIND) == kind.ordinal() && s.tag(id) == tag) {
                    out.add(id);
                }
            }
            return out;
        }

        static int cellX(MatchState s, int id) {
            return s.get(id, Entities.X) >> MatchState.FP_SHIFT;
        }

        static int cellY(MatchState s, int id) {
            return s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
        }

        /** Moves the seat's pawn to the centre of a cell (the cursor stays put with no input). */
        static void putPawn(MatchState s, int seat, int cx, int cy) {
            int pawn = s.pawnId(seat);
            s.set(pawn, Entities.X, at(cx));
            s.set(pawn, Entities.Y, at(cy));
        }

        static ResolvedGame with(ResolvedGame game, ArcadeModule module, String name, int value) {
            return with(game, module, name, new PropertyValue.IntValue(value));
        }

        static ResolvedGame with(ResolvedGame game, ArcadeModule module, String name, String constant) {
            return with(game, module, name, new PropertyValue.EnumValue(constant));
        }

        /** The same game with one property of {@code module} (every stack entry of it) changed. */
        static ResolvedGame with(ResolvedGame g, ArcadeModule module, String name, PropertyValue value) {
            try {
                PropertyValues arenaProps = g.arena() == module ? g.arenaProps().with(name, value) : g.arenaProps();
                PropertyValues pawnProps = g.pawn() == module ? g.pawnProps().with(name, value) : g.pawnProps();
                PropertyValues ruleProps = g.rule() == module ? g.ruleProps().with(name, value) : g.ruleProps();
                List<PropertyValues> hazardProps = new ArrayList<>();
                for (int i = 0; i < g.hazards().size(); i++) {
                    PropertyValues p = g.hazardProps().get(i);
                    hazardProps.add(g.hazards().get(i) == module ? p.with(name, value) : p);
                }
                List<PropertyValues> pickupProps = new ArrayList<>();
                for (int i = 0; i < g.pickups().size(); i++) {
                    PropertyValues p = g.pickupProps().get(i);
                    pickupProps.add(g.pickups().get(i) == module ? p.with(name, value) : p);
                }
                return new ResolvedGame(g.def(), g.arena(), arenaProps, g.pawn(), pawnProps, g.rule(), ruleProps,
                        g.hazards(), hazardProps, g.pickups(), pickupProps, g.control(), g.controlProps(), g.pace(),
                        g.paceProps(), g.modifier(), g.modifierProps(), g.multiplayer(), g.multiplayerProps(),
                        g.theme(), g.themeProps(), g.unknownProperties());
            } catch (ValidationError invalid) {
                throw new IllegalArgumentException(invalid);
            }
        }

        /** 600 ticks of a fixed script, run twice from the same seed, must hash identically. */
        static void assertReplayStable(ResolvedGame game, int seats) {
            long first = HeadlessMatch.of(game, seats, 77L).run(Budgets.COUNTDOWN_TICKS + 600,
                    HeadlessMatch.InputScript.randomWalk()).hash();
            long second = HeadlessMatch.of(game, seats, 77L).run(Budgets.COUNTDOWN_TICKS + 600,
                    HeadlessMatch.InputScript.randomWalk()).hash();
            assertEquals(first, second);
        }
    }
}
