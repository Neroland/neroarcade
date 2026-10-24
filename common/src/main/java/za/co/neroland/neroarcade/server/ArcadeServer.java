package za.co.neroland.neroarcade.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neroarcade.bench.BenchActions;
import za.co.neroland.neroarcade.bus.BusRegistry;
import za.co.neroland.neroarcade.cabinet.AwakeCabinets;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.cabinet.SeatHolder;
import za.co.neroland.neroarcade.cabinet.SeatRegistry;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.exchange.ExchangeActions;

/**
 * Server-side wiring of the runtime-only registries: a player's seat is released when they log out, and every
 * seat, awake slot and rate window is forgotten when the server stops. Registered in common init step 7.
 */
public final class ArcadeServer {

    /**
     * Erasure, seats (review 003b P1, compliance review F1): an erased player who is seated is unseated (ERASED),
     * and the running match forgets their identity, so it can never write them back onto a board or raise a link
     * event for them. A cabinet in an unloaded chunk is skipped by {@link #unseat}: it has already dropped the seat
     * when it fell asleep (SLEEP), so only the registry needs to forget the player (F6). The Exchange's and the
     * Game Bench's per-player rate windows are forgotten. Seats and windows are memory only, so there is no backup
     * to refresh. Conformance probes:
     * {@code SeatRegistry.SERVER.isSeated}, {@code ExchangeActions.holdsWindow}, {@code BenchActions.holdsWindow}.
     */
    public static final NeroArcadeData.StoreEraser SEATS_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "seats";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            if (server != null) {
                unseat(server, player, DropReason.ERASED);
            }
            SeatRegistry.SERVER.forget(player);
            ExchangeActions.forget(player);
            BenchActions.forget(player);
        }

        @Override
        public void backupNow(MinecraftServer server) {
            // memory only: nothing at rest
        }
    };

    private ArcadeServer() {
    }

    /** The stores this class registers for erasure (conformance tests). */
    public static List<NeroArcadeData.StoreEraser> erasers() {
        return List.of(SEATS_ERASER);
    }

    public static void init() {
        NeroArcadeData.addStore(SEATS_ERASER);
        ServerHooks.onPlayerLeave(player -> {
            unseat(player.level().getServer(), player.getUUID(), DropReason.LOGOUT);
            BenchActions.forget(player.getUUID());
        });
        ServerHooks.onStopping(server -> {
            SeatRegistry.SERVER.clear();
            AwakeCabinets.SERVER.clear();
            BusRegistry.SERVER.clear();
            BenchActions.clear();
        });
    }

    /**
     * Drops {@code player}'s seat wherever it is (logout, {@code /neroarcade admin unseat}).
     *
     * @return true if they held a seat
     */
    public static boolean unseat(MinecraftServer server, UUID player, DropReason why) {
        Optional<SeatRegistry.SeatKey> key = SeatRegistry.SERVER.seatOf(player);
        if (key.isEmpty()) {
            return false;
        }
        BlockPos pos = BlockPos.of(key.get().pos());
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(key.get().dimension()) && level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof SeatHolder holder && holder.heldSeat() != null
                    && holder.heldSeat().playerUuid.equals(player)) {
                holder.dropSeat(why);
            }
        }
        SeatRegistry.SERVER.forget(player);
        return true;
    }
}
