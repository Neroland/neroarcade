package za.co.neroland.neroarcade.compat.tournament;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.game.GameDef;

/**
 * Tournaments (003 §10). The events sibling publishes no API in 0.1.0, so nothing provides this seam: it is the
 * shape a future bridge fills. {@link #onResult} hears every credited result (after the leaderboard write);
 * {@link #requests()} is how a tournament would ask a bus to start a match — honoured only while that bus is IDLE or
 * in its LOBBY ({@link #canStart}), and only when a handler is present. The {@link #NO_OP} default ignores results and
 * offers no handler. Any listener that only needs results registers with {@link Tournaments#onResult} instead.
 */
public interface TournamentSeam {

    /** Asks a bus to start a match on a cartridge. */
    interface StartRequestHandler {
        boolean startMatch(ServerLevel level, long busId, GameDef def);
    }

    void onResult(BusMatchResult result);

    Optional<StartRequestHandler> requests();

    /** A start request is honoured only on an idle bus or one waiting in its lobby. */
    static boolean canStart(BusPhase phase) {
        return phase == BusPhase.IDLE || phase == BusPhase.LOBBY;
    }

    TournamentSeam NO_OP = new TournamentSeam() {
        @Override
        public void onResult(BusMatchResult result) {
        }

        @Override
        public Optional<StartRequestHandler> requests() {
            return Optional.empty();
        }
    };
}
