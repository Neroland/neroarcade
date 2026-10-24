package za.co.neroland.neroarcade.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.compat.colonies.MoraleSeam;
import za.co.neroland.neroarcade.compat.economy.CoinOpSeam;
import za.co.neroland.neroarcade.compat.palette.PaletteSeam;
import za.co.neroland.neroarcade.compat.soundtrack.SoundtrackSeam;
import za.co.neroland.neroarcade.compat.tournament.TournamentSeam;
import za.co.neroland.neroarcade.compat.tournament.Tournaments;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §10 / §13 {@code SeamAbsentTest ×5}: with no sibling loaded every seam resolves to its no-op, and every call on
 * it is safe.
 */
class SeamAbsentTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @BeforeEach
    void absent() {
        Seams.reset();
        Seams.resolve(ModPresence.NONE);
    }

    @AfterEach
    void reset() {
        Seams.reset();
    }

    @Nested
    class CoinOp {
        @Test
        void noOpIsFree() {
            assertSame(CoinOpSeam.NO_OP, Seams.coinOp());
            assertFalse(Seams.coinOpBridged());
            assertTrue(Seams.coinOp().chargeSeat(null, ALICE, BlockPos.ZERO, null));
            assertTrue(Seams.coinOp().onDownload(null, ALICE, ALICE, 1, null));
        }

        @Test
        void presentWithoutABridgeStillResolvesSafely() {
            // a loaded sibling whose bridge cannot be built in a plain JVM falls back to the no-op, never throws
            Seams.reset();
            Seams.resolve(id -> id.equals(CoinOpSeam.MOD_ID));
            assertSame(CoinOpSeam.NO_OP, Seams.coinOp());
        }
    }

    @Nested
    class Tournament {
        @Test
        void noOpIgnoresResultsAndOffersNoStart() {
            assertSame(TournamentSeam.NO_OP, Seams.tournament());
            assertTrue(Seams.tournament().requests().isEmpty());
            assertTrue(TournamentSeam.canStart(BusPhase.IDLE) && TournamentSeam.canStart(BusPhase.LOBBY));
            assertFalse(TournamentSeam.canStart(BusPhase.PLAYING));
            List<BusMatchResult> heard = new ArrayList<>();
            Consumer<BusMatchResult> listener = heard::add;
            Tournaments.onResult(listener);
            try {
                BusMatchResult r = new BusMatchResult("abcdefghijklmnop", "abcdefghijklmnop", List.of(), 1, 1L,
                        MatchResult.Outcome.WIN, BusMatchResult.EndedBy.RULE, 0L);
                Tournaments.publish(r);
                assertEquals(List.of(r), heard, "the listener list still hears results");
            } finally {
                Tournaments.remove(listener);
            }
        }
    }

    @Nested
    class Soundtrack {
        @Test
        void noOpUsesTheThemesOwnSound() {
            assertSame(SoundtrackSeam.NO_OP, Seams.soundtrack());
            assertTrue(Seams.soundtrack().diskFor(null).isEmpty());
        }
    }

    @Nested
    class Palette {
        @Test
        void noOpReturnsTheFallbackAndCoreReadsFinishes() {
            assertSame(PaletteSeam.NO_OP, Seams.palette());
            assertEquals(0x123456, Seams.palette().signature("neon_void", 0x123456));
            PaletteSeam.registerFinishes();
            assertEquals(0xFF3FA4, PaletteSeam.CORE.signature("neon_void", 0));
            assertEquals(7, PaletteSeam.CORE.signature("no_such_theme", 7));
            assertEquals(7, PaletteSeam.CORE.signature("Bad Name!", 7));
        }
    }

    @Nested
    class Morale {
        @Test
        void noOpHearsAndDoesNothing() {
            assertSame(MoraleSeam.NO_OP, Seams.morale());
            Seams.morale().onMatchPlayed(null, BlockPos.ZERO);
        }
    }
}
