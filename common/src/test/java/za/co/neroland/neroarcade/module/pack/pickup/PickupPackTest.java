package za.co.neroland.neroarcade.module.pack.pickup;

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
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatState;

/** PICKUP pack registration and stacking by tag. Also hosts {@link Kit}, the pickup tests' helpers. */
class PickupPackTest {

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void registersFourChipsAlphabetically() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        PickupPack.registerAll();
        ModuleRegistry.freeze();
        List<String> ids = ModuleRegistry.byType(ModuleType.PICKUP).stream().map(m -> m.id().toString()).toList();
        assertEquals(List.of("neroarcade:pickup/key", "neroarcade:pickup/score_orb", "neroarcade:pickup/shield",
                "neroarcade:pickup/speed_boost"), ids);
        assertEquals(java.util.Set.of(ModuleFlag.SCORES, ModuleFlag.CLEARABLE), ScoreOrbPickup.INSTANCE.flags());
        assertEquals(java.util.Set.of(ModuleFlag.IS_KEY, ModuleFlag.CLEARABLE), KeyPickup.INSTANCE.flags());
        assertTrue(ShieldPickup.INSTANCE.flags().isEmpty());
        assertTrue(SpeedBoostPickup.INSTANCE.flags().isEmpty());
    }

    @Test
    void stackOfThreeRoutesCollectionByTag() {
        ResolvedGame game = TestModules.resolvedOf(ScoreOrbPickup.INSTANCE, SpeedBoostPickup.INSTANCE,
                ShieldPickup.INSTANCE);
        HeadlessMatch match = Kit.playing(game, 1, 17L);
        MatchState s = match.state();
        assertEquals(3, Kit.count(s, EntityKind.PICKUP, 0));
        assertEquals(1, Kit.count(s, EntityKind.PICKUP, 1));
        assertEquals(1, Kit.count(s, EntityKind.PICKUP, 2));
        Kit.onPawn(s, 0, Kit.ids(s, EntityKind.PICKUP, 1).get(0));
        match.step();
        assertTrue((s.statusFlags(0) & SeatState.SPEED) != 0, "stack 1 is the speed boost");
        assertEquals(0, s.score(0));
        assertEquals(0, s.statusFlags(0) & SeatState.SHIELD);
        Kit.onPawn(s, 0, Kit.ids(s, EntityKind.PICKUP, 2).get(0));
        match.step();
        assertTrue((s.statusFlags(0) & SeatState.SHIELD) != 0, "stack 2 is the shield");
        assertEquals(0, s.score(0));
        Kit.onPawn(s, 0, Kit.ids(s, EntityKind.PICKUP, 0).get(0));
        match.step();
        assertEquals(10, s.score(0), "stack 0 is the orb");
        assertEquals(1, Placement.countMarkers(s, 0));
        assertEquals(1, Placement.countMarkers(s, 1));
        assertEquals(1, Placement.countMarkers(s, 2));
    }

    @Test
    void hazardMarkersAreNotPickupMarkers() {
        HeadlessMatch match = Kit.playing(TestModules.resolvedOf(ScoreOrbPickup.INSTANCE), 1, 3L);
        MatchState s = match.state();
        int foreign = s.spawn(EntityKind.MARKER, 0, 0, -1, 0);
        s.setAux(foreign, s.aux(foreign) | (0xA1 << 20));
        s.set(foreign, Entities.TTL, 500);
        assertEquals(0, Placement.countMarkers(s, 0));
        match.run(5, HeadlessMatch.InputScript.idle());
        assertEquals(3, Kit.count(s, EntityKind.PICKUP, 0));
    }

    /** Pickup-test helpers (package-private; the hazard package has its own copy). */
    static final class Kit {

        private Kit() {
        }

        static int at(int cell) {
            return (cell << MatchState.FP_SHIFT) + MatchState.FP / 2;
        }

        static HeadlessMatch playing(ResolvedGame game, int seats, long seed) {
            return HeadlessMatch.of(game, seats, seed).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
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

        /** Moves entity {@code id} onto the seat's pawn so the next collision pass touches it. */
        static void onPawn(MatchState s, int seat, int id) {
            int pawn = s.pawnId(seat);
            s.set(id, Entities.X, s.get(pawn, Entities.X));
            s.set(id, Entities.Y, s.get(pawn, Entities.Y));
        }

        /** The first pickup of stack 0, moved onto seat 0's pawn and collected by one step. */
        static int collectOne(HeadlessMatch match) {
            MatchState s = match.state();
            int id = ids(s, EntityKind.PICKUP, 0).get(0);
            onPawn(s, 0, id);
            match.step();
            return id;
        }

        /** A lethal HAZARD entity with no chip behind it (the runtime treats it as lethal) on seat 0's pawn. */
        static int hazardOnPawn(MatchState s) {
            int pawn = s.pawnId(0);
            return s.spawn(EntityKind.HAZARD, s.get(pawn, Entities.X), s.get(pawn, Entities.Y), -1, 0);
        }

        static ResolvedGame with(ResolvedGame g, ArcadeModule module, String name, int value) {
            try {
                PropertyValue v = new PropertyValue.IntValue(value);
                List<PropertyValues> pickupProps = new ArrayList<>();
                for (int i = 0; i < g.pickups().size(); i++) {
                    PropertyValues p = g.pickupProps().get(i);
                    pickupProps.add(g.pickups().get(i) == module ? p.with(name, v) : p);
                }
                return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(), g.pawnProps(), g.rule(),
                        g.ruleProps(), g.hazards(), g.hazardProps(), g.pickups(), pickupProps, g.control(),
                        g.controlProps(), g.pace(), g.paceProps(), g.modifier(), g.modifierProps(), g.multiplayer(),
                        g.multiplayerProps(), g.theme(), g.themeProps(), g.unknownProperties());
            } catch (ValidationError invalid) {
                throw new IllegalArgumentException(invalid);
            }
        }

        static void assertReplayStable(ResolvedGame game, int seats) {
            long first = HeadlessMatch.of(game, seats, 77L).run(Budgets.COUNTDOWN_TICKS + 600,
                    HeadlessMatch.InputScript.randomWalk()).hash();
            long second = HeadlessMatch.of(game, seats, 77L).run(Budgets.COUNTDOWN_TICKS + 600,
                    HeadlessMatch.InputScript.randomWalk()).hash();
            assertEquals(first, second);
        }
    }
}
