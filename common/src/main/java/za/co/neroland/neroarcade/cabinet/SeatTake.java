package za.co.neroland.neroarcade.cabinet;

import java.util.function.BooleanSupplier;

import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity.SeatResult;

/** The last steps of taking a seat, pure so the order is unit-tested (review 003b F10). */
final class SeatTake {

    private SeatTake() {
    }

    /**
     * The seat free, the registry claim, the coin-op charge, then the bind. A refused charge releases the claim and
     * binds nothing, so the match never leaves IDLE: no countdown, no start sound, nothing to undo.
     */
    static SeatResult payThenBind(SeatRegistry registry, SeatRegistry.SeatKey key, MatchHost current, int index,
            Seat candidate, BooleanSupplier pay) {
        if (current.seat(index) != null) {
            return SeatResult.TAKEN;
        }
        if (!registry.claim(candidate.playerUuid, key)) {
            return SeatResult.TAKEN;
        }
        if (!pay.getAsBoolean()) {
            registry.release(candidate.playerUuid, key);
            return SeatResult.UNPAID;
        }
        if (!current.bind(index, candidate)) {
            registry.release(candidate.playerUuid, key);
            return current.phase().running() ? SeatResult.IN_PROGRESS : SeatResult.TAKEN;
        }
        return SeatResult.SEATED;
    }
}
