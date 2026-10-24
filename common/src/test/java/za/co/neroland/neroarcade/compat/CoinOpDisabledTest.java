package za.co.neroland.neroarcade.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.compat.economy.CoinOp;
import za.co.neroland.neroarcade.compat.economy.CoinOpSeam;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * 003 §10 / §12 item 8: coin-op ships disabled ({@code economy.coinOp=false}); while it is off, or the price is 0, no
 * seam is ever reached and play and downloads are free; a tombstoned author or the author themselves is never paid; a
 * seam that throws refuses the charge.
 */
class CoinOpDisabledTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    /** Counts calls; refuses everything (so a call that got through shows up as a refusal). */
    private static final class Recording implements CoinOpSeam {
        int calls;

        @Override
        public boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos, String requestKey) {
            calls++;
            return false;
        }

        @Override
        public boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId,
                String requestKey) {
            calls++;
            return false;
        }
    }

    @Test
    void shipsDisabled() {
        assertFalse(NeroArcadeConfig.ECONOMY_COIN_OP.get());
        assertEquals(0, NeroArcadeConfig.ECONOMY_DOWNLOAD_PRICE.get());
    }

    @Test
    void disabledPathNeverReachesTheSeam() {
        Recording seam = new Recording();
        assertTrue(CoinOp.chargeSeat(false, 10, seam, null, ALICE, BlockPos.ZERO));
        assertTrue(CoinOp.onDownload(false, 10, seam, null, BOB, ALICE, 1));
        assertTrue(CoinOp.chargeSeat(true, 0, seam, null, ALICE, BlockPos.ZERO), "price 0 is free");
        assertTrue(CoinOp.onDownload(true, 0, seam, null, BOB, ALICE, 1));
        assertTrue(CoinOp.onDownload(true, 10, seam, null, BOB, Tombstones.ERASED_AUTHOR, 1), "a tombstone is never paid");
        assertTrue(CoinOp.onDownload(true, 10, seam, null, ALICE, ALICE, 1), "your own game is free");
        assertEquals(0, seam.calls);

        assertFalse(CoinOp.chargeSeat(true, 10, seam, null, ALICE, BlockPos.ZERO), "on: the seam decides");
        assertFalse(CoinOp.onDownload(true, 10, seam, null, BOB, ALICE, 1));
        assertEquals(2, seam.calls);
    }

    @Test
    void aThrowingSeamRefuses() {
        CoinOpSeam broken = new CoinOpSeam() {
            @Override
            public boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos, String requestKey) {
                throw new IllegalStateException("boom");
            }

            @Override
            public boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId,
                    String requestKey) {
                throw new NoSuchMethodError("changed api");
            }
        };
        assertFalse(CoinOp.chargeSeat(true, 10, broken, null, ALICE, BlockPos.ZERO));
        assertFalse(CoinOp.onDownload(true, 10, broken, null, BOB, ALICE, 1));
        assertTrue(CoinOp.chargeSeat(true, 10, CoinOpSeam.NO_OP, null, ALICE, BlockPos.ZERO), "no economy: free");
    }
}
