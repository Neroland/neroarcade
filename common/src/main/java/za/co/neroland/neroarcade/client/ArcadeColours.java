package za.co.neroland.neroarcade.client;

/**
 * Every hardware and GUI colour of NeroArcade (004 §1, §2, §8), byte for byte. World colours are
 * {@code 0xRRGGBB}; GUI chrome is {@code 0xAARRGGBB} (opaque unless noted). Pure Java so tests can read it.
 */
public final class ArcadeColours {

    // --- hardware (0xRRGGBB, 004 §1) ---
    public static final int BODY = 0x141A28;
    public static final int BODY_DARK = 0x0B0F1A;
    public static final int BODY_LIGHT = 0x2A3140;
    public static final int TRIM = 0x3A4458;
    public static final int INK = 0x05070D;
    public static final int TEXT_LIGHT = 0xE8ECF4;
    public static final int TEXT_SUBTLE = 0x8A93A6;
    public static final int NEON = 0xFF3FA4;
    public static final int CYAN = 0x2FE3FF;
    public static final int AMBER = 0xFFB347;
    public static final int DENY = 0xFF5C5C;
    public static final int OK = 0x58E07A;

    /** Packed light for emissive BER quads (sky 15, block 15). */
    public static final int FULL_BRIGHT = 0x00F000F0;

    // --- GUI chrome (0xAARRGGBB; the NeroSecurity hull, 004 §8) ---
    public static final int PANEL = 0xFF141C26;
    public static final int EDGE = 0xFF2A3A4D;
    public static final int INNER = 0xFF05080D;
    public static final int TROUGH = 0xFF0B0F1A;
    public static final int TITLE = 0xFFE8ECF4;
    public static final int SUBTLE = 0xFF8A93A6;
    public static final int DENY_TEXT = 0xFFFF7A7A;
    public static final int OK_TEXT = 0xFF58E07A;
    public static final int AMBER_TEXT = 0xFFFFB347;
    public static final int CYAN_TEXT = 0xFF2FE3FF;
    public static final int BUTTON_BODY = 0xFF1B2433;
    public static final int BUTTON_HOVER = 0xFF243047;
    public static final int BUTTON_EDGE = 0xFF3A4458;
    public static final int EDGE_HOT = 0xFF2FE3FF;
    public static final int DISABLED_BODY = 0xFF151A22;
    public static final int DISABLED_EDGE = 0xFF262C36;
    public static final int DISABLED_TEXT = 0xFF5A6273;
    public static final int DANGER_EDGE = 0xFFFF5C5C;
    public static final int SLIDER_TROUGH = 0xFF0B0F1A;
    public static final int SLOT_FRAME = 0xFF0B0F1A;
    public static final int SELECTED_ROW = 0x332FE3FF;
    public static final int HUD_BACK = 0xC005070D;

    private ArcadeColours() {
    }

    /** {@code 0xRRGGBB} → opaque {@code 0xFFRRGGBB}. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** {@code 0xRRGGBB} with an alpha byte. */
    public static int argb(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
