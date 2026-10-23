package za.co.neroland.neroarcade.server;

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

/**
 * Server-side wiring of the runtime-only registries: a player's seat is released when they log out, and every
 * seat, awake slot and rate window is forgotten when the server stops. Registered in common init step 7.
 */
public final class ArcadeServer {

    private ArcadeServer() {
    }

    public static void init() {
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
