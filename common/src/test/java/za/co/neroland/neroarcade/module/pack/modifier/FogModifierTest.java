package za.co.neroland.neroarcade.module.pack.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

class FogModifierTest {

    @Test
    void setsRadius() {
        MatchState state = HeadlessMatch.of(TestModules.resolvedOf(FogModifier.INSTANCE), 1, 71L).state();
        assertEquals(5, state.fogRadius());
        assertEquals(0, HeadlessMatch.of(TestModules.resolvedOf(), 1, 71L).state().fogRadius());
    }

    /**
     * Fog is a render hint: the same seed and script evolve identically with and without it. The hash differs only
     * because {@code fogRadius} is in the keyframe, so this compares the simulated fields directly.
     */
    @Test
    void hashUnchangedVersusNoFog() {
        ResolvedGame fogged = TestModules.resolvedOf(FogModifier.INSTANCE, TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        ResolvedGame clear = TestModules.resolvedOf(TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        MatchState a = HeadlessMatch.of(fogged, 1, 72L).run(600, HeadlessMatch.InputScript.randomWalk()).state();
        MatchState b = HeadlessMatch.of(clear, 1, 72L).run(600, HeadlessMatch.InputScript.randomWalk()).state();

        assertEquals(5, a.fogRadius());
        assertEquals(0, b.fogRadius());
        assertNotEquals(a.stateHash(), b.stateHash(), "fogRadius is part of the keyframe");

        assertEquals(b.tick(), a.tick());
        assertEquals(b.phase(), a.phase());
        assertEquals(b.timerTicks(), a.timerTicks());
        assertEquals(b.rngState(), a.rngState());
        assertEquals(b.gravity(), a.gravity());
        assertEquals(b.wrapsX(), a.wrapsX());
        assertEquals(b.wrapsY(), a.wrapsY());
        assertEquals(b.width() * b.height(), a.width() * a.height());
        for (int i = 0; i < a.width() * a.height(); i++) {
            assertEquals(b.cellAt(i), a.cellAt(i), "cell " + i);
        }
        assertEquals(b.entityCount(), a.entityCount());
        for (int i = 0; i < a.entityCount(); i++) {
            int id = a.entityIdAt(i);
            assertEquals(b.entityIdAt(i), id);
            for (int field = 1; field < MatchState.ENTITY_STRIDE; field++) {
                assertEquals(b.get(id, field), a.get(id, field), "entity " + id + " field " + field);
            }
            assertEquals(b.aux(id), a.aux(id));
        }
        assertEquals(b.aliveSeatMask(), a.aliveSeatMask());
        for (int s = 0; s < a.seatCount(); s++) {
            assertEquals(b.score(s), a.score(s));
            assertEquals(b.lives(s), a.lives(s));
            assertEquals(b.pawnId(s), a.pawnId(s));
            assertEquals(b.statusFlags(s), a.statusFlags(s));
        }
    }

    @Test
    void replayStable() {
        ResolvedGame game = TestModules.resolvedOf(FogModifier.INSTANCE, TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        long a = HeadlessMatch.of(game, 1, 1009L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(game, 1, 1009L).run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
