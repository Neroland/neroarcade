package za.co.neroland.neroarcade.module.pack.pawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/** 008 §2.2 {@code pawn/cursor} refinements: BUTTONS_ONLY auto-move, the VOID check, projectile duty. */
class CursorPawnTest {

    private static ResolvedGame withSpeed(ResolvedGame g, int speed) throws ValidationError {
        return new ResolvedGame(g.def(), g.arena(), g.arenaProps(), g.pawn(),
                g.pawnProps().with("speed", new PropertyValue.IntValue(speed)), g.rule(), g.ruleProps(), g.hazards(),
                g.hazardProps(), g.pickups(), g.pickupProps(), g.control(), g.controlProps(), g.pace(), g.paceProps(),
                g.modifier(), g.modifierProps(), g.multiplayer(), g.multiplayerProps(), g.theme(), g.themeProps(),
                g.unknownProperties());
    }

    private static HeadlessMatch playing(ResolvedGame game) {
        return HeadlessMatch.of(game, 1, 1L).run(Budgets.COUNTDOWN_TICKS, HeadlessMatch.InputScript.idle());
    }

    private static int cellX(MatchState s) {
        return s.get(s.pawnId(0), Entities.X) >> MatchState.FP_SHIFT;
    }

    @Test
    void buttonsOnlyAutoMoves() throws ValidationError {
        HeadlessMatch match = playing(withSpeed(TestModules.resolvedOf(CursorPawn.INSTANCE,
                TestModules.control(ModuleFlag.BUTTONS_ONLY)), 1));
        MatchState s = match.state();
        int start = cellX(s);
        match.run(20, HeadlessMatch.InputScript.idle());
        int moved = cellX(s);
        assertTrue(moved - start >= 3, "crossed " + (moved - start) + " cells right with no input");
        match.step(SeatInput.of(SeatInput.A, 0, 0));
        match.run(8, HeadlessMatch.InputScript.idle());
        assertTrue(cellX(s) < moved, "the A edge reversed it");
        int held = s.get(s.pawnId(0), Entities.X);
        match.run(5, (tick, seat, rng) -> SeatInput.of(SeatInput.B, 0, 0));
        match.step(SeatInput.of(SeatInput.B, 0, 0));
        int stopped = s.get(s.pawnId(0), Entities.X);
        match.step(SeatInput.of(SeatInput.B, 0, 0));
        assertEquals(stopped, s.get(s.pawnId(0), Entities.X), "B held stops it");
        assertTrue(stopped <= held);
    }

    @Test
    void fourWayCursorIsUnchangedWithoutInput() {
        HeadlessMatch match = playing(TestModules.resolvedOf(CursorPawn.INSTANCE));
        MatchState s = match.state();
        int x = s.get(s.pawnId(0), Entities.X);
        match.run(20, HeadlessMatch.InputScript.idle());
        assertEquals(x, s.get(s.pawnId(0), Entities.X), "no input, no motion");
    }

    @Test
    void voidKills() {
        HeadlessMatch match = playing(TestModules.resolvedOf(CursorPawn.INSTANCE));
        MatchState s = match.state();
        int pawn = s.pawnId(0);
        s.setCell(s.get(pawn, Entities.X) >> MatchState.FP_SHIFT, s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT, Cell.VOID);
        assertTrue(s.seatAlive(0));
        match.step();
        assertFalse(s.seatAlive(0), "standing on VOID at the end of its tick costs the only life");
    }

    @Test
    void movesItsOwnProjectiles() {
        HeadlessMatch match = playing(TestModules.resolvedOf(CursorPawn.INSTANCE));
        MatchState s = match.state();
        int shot = s.spawn(EntityKind.PROJECTILE, (5 << 8) + 128, (8 << 8) + 128, 0, 0);
        s.set(shot, Entities.VX, 256);
        match.step();
        assertEquals(6, s.get(shot, Entities.X) >> MatchState.FP_SHIFT);
        for (int i = 0; i < 12; i++) {
            match.step();
        }
        assertFalse(s.exists(shot), "removed entering the border wall");
    }

    @Test
    void replayStable() {
        long a = HeadlessMatch.of(TestModules.resolvedOf(CursorPawn.INSTANCE), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        long b = HeadlessMatch.of(TestModules.resolvedOf(CursorPawn.INSTANCE), 1, 3L)
                .run(600, HeadlessMatch.InputScript.randomWalk()).hash();
        assertEquals(a, b);
    }
}
