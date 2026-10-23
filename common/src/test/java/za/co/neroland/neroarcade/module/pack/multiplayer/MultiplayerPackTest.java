package za.co.neroland.neroarcade.module.pack.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.pack.ArcadeModules;
import za.co.neroland.neroarcade.module.pack.rule.SurviveTimeRule;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The MULTIPLAYER pack registers in id order and declares the 008 §2.9 flags and props; plus the shared
 * fixtures the per-chip tests use (a resolved game with chosen MULTIPLAYER and RULE properties, a match run
 * through its countdown, a hook context, an asymmetric multi-seat script).
 */
class MultiplayerPackTest {

    @AfterEach
    void restore() {
        TestModules.install();
    }

    @Test
    void registersTheThreeNonDefaultChipsInIdOrder() {
        TestModules.reset();
        ArcadeModules.registerDefaults();
        MultiplayerPack.registerAll();
        ModuleRegistry.freeze();
        List<ArcadeModule> multiplayer = ModuleRegistry.byType(ModuleType.MULTIPLAYER);
        assertEquals(4, multiplayer.size());
        List<String> ids = List.of("neroarcade:multiplayer/coop", "neroarcade:multiplayer/turns",
                "neroarcade:multiplayer/versus");
        List<String> declared = List.of(CoopMultiplayer.INSTANCE.id().toString(),
                TurnsMultiplayer.INSTANCE.id().toString(), VersusMultiplayer.INSTANCE.id().toString());
        assertEquals(ids, declared);
        assertEquals(ids.stream().sorted().toList(), ids, "registerAll goes in alphabetical id order");
        for (String id : ids) {
            assertTrue(multiplayer.stream().anyMatch(m -> m.id().toString().equals(id)), id);
        }
        assertTrue(multiplayer.stream().anyMatch(m -> m.id().equals(ModuleDefaults.MULTIPLAYER)));
    }

    @Test
    void flagsSeatsAndLivesFollowTheSpec() {
        assertTrue(CoopMultiplayer.INSTANCE.flags().isEmpty());
        assertEquals(Set.of(ModuleFlag.MULTI_SEAT_ONLY, ModuleFlag.RUNTIME_OPPONENT), VersusMultiplayer.INSTANCE.flags());
        assertEquals(Set.of(ModuleFlag.MULTI_SEAT_ONLY), TurnsMultiplayer.INSTANCE.flags());
        for (MultiplayerModule m : List.<MultiplayerModule>of(CoopMultiplayer.INSTANCE, VersusMultiplayer.INSTANCE,
                TurnsMultiplayer.INSTANCE)) {
            assertEquals(2, m.seats(PropertyValues.defaults(m)), m.id().toString());
            assertEquals(4, m.seats(withInt(m, "seats", 4)), m.id().toString());
            assertEquals(4, game(m, withInt(m, "seats", 4)).seats(), m.id().toString());
            assertTrue(m.properties().size() <= 3, m.id().toString());
        }
        assertEquals(30, PropertyValues.defaults(TurnsMultiplayer.INSTANCE).getInt("turn_seconds"));
        assertTrue(CoopMultiplayer.INSTANCE.sharesLives(PropertyValues.defaults(CoopMultiplayer.INSTANCE)));
        assertFalse(CoopMultiplayer.INSTANCE.sharesLives(withBool(CoopMultiplayer.INSTANCE, "shared_lives", false)));
        assertFalse(VersusMultiplayer.INSTANCE.sharesLives(PropertyValues.defaults(VersusMultiplayer.INSTANCE)));
        assertFalse(TurnsMultiplayer.INSTANCE.sharesLives(PropertyValues.defaults(TurnsMultiplayer.INSTANCE)));
        assertTrue(VersusMultiplayer.INSTANCE.runtimeOpponent(PropertyValues.defaults(VersusMultiplayer.INSTANCE)));
        assertFalse(CoopMultiplayer.INSTANCE.runtimeOpponent(PropertyValues.defaults(CoopMultiplayer.INSTANCE)));
        assertFalse(TurnsMultiplayer.INSTANCE.runtimeOpponent(PropertyValues.defaults(TurnsMultiplayer.INSTANCE)));
    }

    @Test
    void effectArgsCarryTheLiveValues() {
        Object[] coop = CoopMultiplayer.INSTANCE.effectArgs(PropertyValues.defaults(CoopMultiplayer.INSTANCE));
        assertEquals(2, coop.length);
        assertEquals(2, coop[0]);
        assertEquals(1, VersusMultiplayer.INSTANCE.effectArgs(withInt(VersusMultiplayer.INSTANCE, "seats", 3)).length);
        assertEquals(3, VersusMultiplayer.INSTANCE.effectArgs(withInt(VersusMultiplayer.INSTANCE, "seats", 3))[0]);
        Object[] turns = TurnsMultiplayer.INSTANCE.effectArgs(PropertyValues.defaults(TurnsMultiplayer.INSTANCE));
        assertEquals(2, turns[0]);
        assertEquals(30, turns[1]);
    }

    // ---- shared fixtures ------------------------------------------------------------------------------

    static PropertyValues withInt(ArcadeModule module, String name, int value) {
        return with(PropertyValues.defaults(module), name, new PropertyValue.IntValue(value));
    }

    static PropertyValues withBool(ArcadeModule module, String name, boolean value) {
        return with(PropertyValues.defaults(module), name, new PropertyValue.BoolValue(value));
    }

    static PropertyValues with(PropertyValues base, String name, PropertyValue value) {
        try {
            return base.with(name, value);
        } catch (ValidationError e) {
            throw new IllegalArgumentException(name + " = " + value, e);
        }
    }

    /** {@code multiplayer} with {@code props}; every other slot default (survive_time at its defaults). */
    static ResolvedGame game(MultiplayerModule multiplayer, PropertyValues props) {
        return game(multiplayer, props, PropertyValues.defaults(SurviveTimeRule.INSTANCE));
    }

    /** As {@link #game(MultiplayerModule, PropertyValues)} with survive_time's properties chosen too. */
    static ResolvedGame game(MultiplayerModule multiplayer, PropertyValues props, PropertyValues ruleProps) {
        ResolvedGame g = TestModules.resolvedOf(multiplayer);
        return new ResolvedGame(g.def(),
                g.arena(), g.arenaProps(),
                g.pawn(), g.pawnProps(),
                g.rule(), ruleProps,
                g.hazards(), g.hazardProps(),
                g.pickups(), g.pickupProps(),
                g.control(), g.controlProps(),
                g.pace(), g.paceProps(),
                g.modifier(), g.modifierProps(),
                g.multiplayer(), props,
                g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    /** survive_time with {@code seconds} and {@code lives}. */
    static PropertyValues survive(int seconds, int lives) {
        return with(withInt(SurviveTimeRule.INSTANCE, "seconds", seconds), "lives", new PropertyValue.IntValue(lives));
    }

    /** Starts the match and runs its countdown, so the next step simulates tick 0. */
    static HeadlessMatch playing(ResolvedGame game, int seats, long seed) {
        HeadlessMatch match = HeadlessMatch.of(game, seats, seed);
        match.run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
        assertEquals(0, match.state().tick(), "the countdown does not advance the simulated tick");
        return match;
    }

    /** A hook context over the match's live state, carrying the MULTIPLAYER chip's properties. */
    static ModuleContext ctx(HeadlessMatch match) {
        MatchState s = match.state();
        ResolvedGame g = match.runtime().game();
        return new ModuleContext(s, g.multiplayerProps(), Rng.view(s), g, s.tick(), 0);
    }

    /**
     * Four scripted seats that never press the same thing: seat 0 sweeps right/down, seat 1 up/left tapping A,
     * seat 2 down/left, seat 3 mostly idle tapping B. Deterministic in {@code tick}; never draws from the RNG.
     */
    static HeadlessMatch.InputScript asymmetric() {
        return (tick, seat, rng) -> switch (seat) {
            case 0 -> SeatInput.of((tick / 7) % 2 == 0 ? SeatInput.RIGHT : SeatInput.DOWN, 0, 0);
            case 1 -> SeatInput.of(((tick / 11) % 3 == 0 ? SeatInput.UP : SeatInput.LEFT)
                    | (tick % 13 == 0 ? SeatInput.A : 0), 0, 0);
            case 2 -> SeatInput.of((tick / 5) % 2 == 0 ? SeatInput.DOWN : SeatInput.LEFT, 0, 0);
            default -> SeatInput.of(tick % 17 == 0 ? SeatInput.B : 0, 0, 0);
        };
    }

    /** 001 §9 per-chip replay hash: 600 simulated ticks of a fixed script, run twice, same hash. */
    static long assertReplayStable(ResolvedGame game, int seats) {
        long first = playing(game, seats, 0x5EEDL).run(600, asymmetric()).hash();
        long second = playing(game, seats, 0x5EEDL).run(600, asymmetric()).hash();
        assertEquals(first, second, game.multiplayer().id() + " is not replay-stable");
        return first;
    }
}
