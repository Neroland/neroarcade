package za.co.neroland.neroarcade.link;

/**
 * The one place the link module turns a cartridge into the leaderboard store's board key.
 *
 * <p>Owner decision (after the Stage 4 review): leaderboards are keyed by <b>cartridge id</b> — duplicates and
 * downloads keep the id and share a board, a remix mints a new id and gets a fresh board. Everything the link module
 * sends (the {@code leaderboard?cartridge=} parameter, {@code RECORD_BEATEN}/{@code MATCH_RESULT} {@code id}, the
 * notification window, the alert id and the deep link) uses the cartridge id, and so does the store (D-owner-1,
 * 2026-10-23). {@code lineageRoot} is accepted for call-site compatibility and ignored.
 */
final class BoardKeys {

    private BoardKeys() {
    }

    /** The leaderboard store's key for the board of cartridge {@code cartridgeId} (lineage {@code lineageRoot}). */
    static String cartridgeKey(String cartridgeId, String lineageRoot) {
        return cartridgeId;
    }
}
