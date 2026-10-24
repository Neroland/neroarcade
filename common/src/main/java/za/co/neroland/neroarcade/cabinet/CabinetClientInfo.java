package za.co.neroland.neroarcade.cabinet;

import java.util.Optional;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.item.CartridgeTint;

/**
 * What a client knows about a cabinet's cartridge, from the block entity's update tag — and nothing more: the
 * title for the attract card, the theme tint, whether this server can run it (and if not, the validator's code
 * for the "UNREADABLE HERE" screen), and the game definition <b>without its author UUID and without its credit</b>
 * so the screen can draw the right sprites and the seated player's pawn can be predicted. The update tag reaches
 * every client in range and no client draws a credit there, so none is sent (compliance review F3); the credit is
 * shown only on the item tooltip, the bench screen, the Exchange page and {@code /neroarcade cartridge inspect}.
 *
 * @param errorCode a {@code ValidationError.Code} name, or "" when readable / no cartridge
 */
public record CabinetClientInfo(boolean hasCartridge, String title, int tint, boolean readable, String errorCode,
        Optional<GameDef> def) {

    public static final String KEY_TITLE = "title";
    public static final String KEY_TINT = "tint";
    public static final String KEY_READABLE = "readable";
    public static final String KEY_ERROR = "error";
    public static final String KEY_DEF = "def";

    public static final CabinetClientInfo EMPTY = new CabinetClientInfo(false, "", CartridgeTint.GREY, false, "",
            Optional.empty());

    public CabinetClientInfo {
        title = title == null ? "" : title;
        errorCode = errorCode == null ? "" : errorCode;
        def = def == null ? Optional.empty() : def.map(CabinetClientInfo::anonymous);
    }

    /** The definition with the author UUID and the credit removed (everything the screen needs kept). */
    public static GameDef anonymous(GameDef def) {
        if (def.authorUuid().isEmpty() && def.authorCredit().isEmpty()) {
            return def;
        }
        return new GameDef(def.schemaVersion(), def.id(), def.title(), Optional.empty(), Optional.empty(),
                def.lineage(), def.seed(), def.slots(), def.createdAt(), def.revision());
    }
}
