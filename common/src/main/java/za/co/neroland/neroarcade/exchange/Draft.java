package za.co.neroland.neroarcade.exchange;

import java.util.UUID;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * A game waiting in its owner's drafts bucket at the Arcade Exchange (003 §8.2): put there by NeroLink's
 * {@code import_cartridge} (Stage 7), fetched onto a Blank Cartridge at a kiosk, purged after 30 days. Only the
 * owner ever sees it.
 *
 * @param createdAt epoch seconds the draft was added (the 30-day clock)
 */
public record Draft(UUID owner, GameDef gameDef, long createdAt) {

    public String cartridgeId() {
        return gameDef.id();
    }
}
