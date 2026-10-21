package za.co.neroland.neroarcade.cabinet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.neroarcade.network.LeaveSeatPayload;
import za.co.neroland.neroarcade.runtime.net.SeatInputPayload;

/**
 * Server handlers for the two seat payloads. The sender is the connection's player — never a UUID from the
 * payload — and the addressed block must be a loaded {@link SeatHolder} in the sender's own dimension.
 */
public final class SeatPackets {

    private SeatPackets() {
    }

    /** {@code SeatInputPayload}: gate (UUID, match id, rate) then latch; every refusal is silent and counted. */
    public static void onInput(SeatInputPayload payload, ServerPlayer player) {
        SeatHolder holder = holderAt(player, payload.cabinet());
        if (holder == null) {
            return;
        }
        MatchHost host = holder.matchHost();
        Seat seat = holder.heldSeat();
        if (host == null || seat == null) {
            holder.countRejectedInput();
            return;
        }
        InputGate.Verdict verdict = host.offer(seat.seat, player.getUUID(), payload.matchId(), payload.input());
        if (verdict == InputGate.Verdict.NO_SEAT || verdict == InputGate.Verdict.WRONG_PLAYER) {
            holder.countRejectedInput();
        }
    }

    /** {@code LeaveSeatPayload}: honoured only for the sender's own seat. */
    public static void onLeave(LeaveSeatPayload payload, ServerPlayer player) {
        SeatHolder holder = holderAt(player, payload.holder());
        if (holder == null) {
            return;
        }
        Seat seat = holder.heldSeat();
        if (seat != null && seat.playerUuid.equals(player.getUUID())) {
            holder.dropSeat(DropReason.ESC);
        }
    }

    private static SeatHolder holderAt(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof SeatHolder holder ? holder : null;
    }
}
