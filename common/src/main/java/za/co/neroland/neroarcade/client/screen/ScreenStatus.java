package za.co.neroland.neroarcade.client.screen;

/**
 * The four non-game screens of 004 §5. Each has a lang key for anything outside the pixel screen (tooltips, the
 * HUD); the pixel screen itself uses the bitmap font's fixed upper-case words.
 */
public enum ScreenStatus {
    NO_CARTRIDGE("neroarcade.cabinet.status.no_cartridge"),
    NO_POWER("neroarcade.cabinet.status.no_power"),
    UNREADABLE("neroarcade.cabinet.status.unreadable"),
    LINKED_WAITING("neroarcade.cabinet.status.linked");

    public final String langKey;

    ScreenStatus(String langKey) {
        this.langKey = langKey;
    }
}
