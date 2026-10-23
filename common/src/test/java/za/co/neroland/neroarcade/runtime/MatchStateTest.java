package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.pack.pawn.CursorPawn;

class MatchStateTest {

    /** A small, fully determined state touching every serialised field. */
    static MatchState sample() {
        MatchState s = new MatchState(8, 6, 16, 2);
        s.setCell(0, 0, Cell.WALL);
        s.setCell(3, 2, Cell.EXIT);
        int pawn = s.spawn(EntityKind.PAWN, 300, 400, 0, 0);
        s.setPawnId(0, pawn);
        int hazard = s.spawn(EntityKind.HAZARD, 1000, 900, -1, 2);
        s.set(hazard, Entities.VX, -64);
        s.set(hazard, Entities.TTL, 50);
        s.setAux(hazard, 0x1234_5602);
        s.addScore(1, 77);
        s.setLives(1, 3);
        s.setStatus(0, SeatState.SHIELD, 40);
        s.setTimerTicks(1200);
        s.setWinners(2);
        s.rngState = 0x0123_4567_89AB_CDEFL;
        s.tick = 321;
        s.phase = MatchPhase.PLAYING;
        return s;
    }

    @Test
    void spawnAndRemoveKeepIdsSorted() {
        MatchState s = new MatchState(8, 8, 32, 1);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(s.spawn(EntityKind.MARKER, i << 8, 0, -1, 0));
        }
        s.remove(ids.get(3));
        s.remove(ids.get(10));
        s.remove(12345);
        assertEquals(18, s.entityCount());
        for (int i = 1; i < s.entityCount(); i++) {
            assertTrue(s.entityIdAt(i - 1) < s.entityIdAt(i));
        }
        List<Integer> visited = new ArrayList<>();
        s.forEach(EntityKind.MARKER, id -> {
            visited.add(id);
            if (id % 2 == 0) {
                s.remove(id);
            }
        });
        assertEquals(18, visited.size());
        for (int i = 1; i < visited.size(); i++) {
            assertTrue(visited.get(i - 1) < visited.get(i));
        }
    }

    @Test
    void idsSkipTheNoneSentinelAndStaySortedAcrossTheWrap() {
        MatchState s = new MatchState(8, 8, 8, 1);
        int survivor = s.spawn(EntityKind.MARKER, 0, 0, -1, 0);
        s.nextEntityId = 0xFFFD;
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(s.spawn(EntityKind.MARKER, 0, 0, -1, 0));
        }
        assertEquals(List.of(0xFFFD, 0xFFFE, 1, 2), ids, "0 is live (survivor) and 0xFFFF is never an id");
        assertEquals(0, survivor);
        for (int i = 1; i < s.entityCount(); i++) {
            assertTrue(s.entityIdAt(i - 1) < s.entityIdAt(i));
        }
        assertTrue(s.find(0xFFFF) < 0);
    }

    @Test
    void copyIsDeep() {
        MatchState a = sample();
        MatchState b = a.copy();
        assertEquals(a.stateHash(), b.stateHash());
        b.addScore(0, 1);
        b.set(b.entityIdAt(0), Entities.X, 5);
        b.setCell(1, 1, Cell.WALL);
        assertNotEquals(a.stateHash(), b.stateHash());
        assertEquals(0, a.score(0));
        assertEquals(300, a.get(a.entityIdAt(0), Entities.X));
        assertEquals(Cell.EMPTY, a.cell(1, 1));
    }

    @Test
    void stateHashIsPinned() {
        assertEquals(StateHashTest.SAMPLE_HASH, sample().stateHash(),
                () -> String.format("sample hash changed: 0x%016XL", sample().stateHash()));
    }

    @Test
    void apiBounds() {
        MatchState s = sample();
        assertThrows(IllegalArgumentException.class, () -> s.get(999, Entities.X));
        assertThrows(IllegalArgumentException.class, () -> s.set(s.entityIdAt(0), Entities.ID, 7));
        assertThrows(IllegalArgumentException.class, () -> s.get(s.entityIdAt(0), MatchState.ENTITY_STRIDE));
        assertEquals(Cell.WALL, s.cell(-1, 0));
        assertEquals(Cell.WALL, s.cell(0, 99));
        s.setCell(-1, -1, Cell.EXIT);
        s.setWrap(true, false);
        assertEquals(s.cell(0, 2), s.cell(8, 2));
        assertEquals(0, s.wrapX(8 << 8));
        assertEquals(-5, s.wrapY(-5));
        s.addScore(9, 5);
        assertEquals(0, s.score(9));
        assertEquals(-1, s.pawnId(3));
    }

    @Test
    void damageRespectsShieldLivesAndRespawn() {
        MatchState s = sample();
        s.damageSeat(0, 1);
        assertEquals(1, s.lives(0), "shield absorbs");
        s.setStatus(0, SeatState.SHIELD, 0);
        s.damageSeat(0, 1);
        assertEquals(0, s.lives(0));
        assertEquals(-1, s.pawnId(0));
        assertTrue(!s.seatAlive(0));
        s.damageSeat(1, 1);
        assertEquals(2, s.lives(1));
        assertEquals(Budgets.RESPAWN_TICKS, s.respawnTicks(1));
        s.damageSeat(1, 1);
        assertEquals(2, s.lives(1), "no damage while respawning");
    }

    @Test
    void sharedLivesPool() {
        MatchState s = new MatchState(8, 8, 8, 2);
        s.sharedLives = 2;
        s.setPawnId(0, s.spawn(EntityKind.PAWN, 0, 0, 0, 0));
        s.setPawnId(1, s.spawn(EntityKind.PAWN, 0, 0, 1, 0));
        s.damageSeat(0, 1);
        assertEquals(1, s.sharedLives());
        assertEquals(1, s.lives(1));
        s.damageSeat(1, 1);
        assertEquals(0, s.aliveSeatMask());
        assertEquals(0, s.entityCount());
    }

    /** 001 A15.d: a body's shots go with it; another seat's shots and ownerless entities stay. */
    @Test
    void removeSeatBodyDropsOwnedProjectiles() {
        MatchState s = new MatchState(8, 8, 16, 2);
        s.setPawnId(0, s.spawn(EntityKind.PAWN, 0, 0, 0, 0));
        s.setPawnId(1, s.spawn(EntityKind.PAWN, 0, 0, 1, 0));
        s.spawn(EntityKind.SEGMENT, 0, 0, 0, 0);
        s.spawn(EntityKind.PROJECTILE, 256, 256, 0, 0);
        s.spawn(EntityKind.PROJECTILE, 512, 256, 0, 0);
        int theirs = s.spawn(EntityKind.PROJECTILE, 768, 256, 1, 0);
        int hazard = s.spawn(EntityKind.HAZARD, 768, 512, -1, 0);
        s.removeSeatBody(0);
        assertEquals(-1, s.pawnId(0));
        assertEquals(0, s.count(EntityKind.SEGMENT));
        assertEquals(1, s.count(EntityKind.PROJECTILE), "only seat 1's shot is left");
        assertTrue(s.exists(theirs));
        assertTrue(s.exists(hazard));
        assertTrue(s.exists(s.pawnId(1)));
    }

    @Test
    void solidEntitiesBlock() {
        MatchState s = new MatchState(8, 8, 8, 1);
        int rock = s.spawn(EntityKind.HAZARD, (4 << 8) + 128, (2 << 8) + 128, -1, 0);
        assertTrue(!s.solidAt(4, 2), "not solid without F_SOLID");
        s.set(rock, Entities.FLAGS, Entities.F_SOLID);
        assertTrue(s.solidAt(4, 2));
        assertTrue(!s.solidAt(3, 2));
        s.setWrap(true, false);
        assertTrue(s.solidAt(12, 2), "wraps like cell()");
        s.setWrap(false, false);

        // the cursor stops in front of it and still moves freely elsewhere
        int pawn = s.spawn(EntityKind.PAWN, (1 << 8) + 128, (2 << 8) + 128, 0, 0);
        s.setPawnId(0, pawn);
        ModuleContext ctx = new ModuleContext(s, null, Rng.view(s), null, 0, 0);
        s.set(pawn, Entities.VX, MatchState.FP);
        for (int i = 0; i < 4; i++) {
            CursorPawn.INSTANCE.tick(ctx, 0);
        }
        assertEquals(3, s.get(pawn, Entities.X) >> MatchState.FP_SHIFT, "blocked by the solid entity at x=4");
        s.set(pawn, Entities.VX, 0);
        s.set(pawn, Entities.VY, MatchState.FP);
        CursorPawn.INSTANCE.tick(ctx, 0);
        assertEquals(3, s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT, "other directions are free");
    }
}
