package za.co.neroland.neroarcade.client.input;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The arcade controls (category {@code key.category.neroarcade.arcade} in Controls): a four-way pad (default
 * W A S D), A (Space), B (Left Shift) and an aim pad for twin-stick games (arrow keys). Defaults share keys with
 * vanilla movement on purpose: while the player is seated the seat swallows movement ({@code SeatedInput}), so W
 * steers the pawn instead of walking away from the cabinet. A doubles as START in the lobby and on the results
 * strip (002 §7). Esc leaves the seat ({@code ArcadeClient}).
 *
 * <p>Two input sources: in the world, the mappings' own pressed state; inside the Game Bench screen during a test
 * play (where vanilla does not update key mappings), the screen's key events through {@link #screenKey}.
 * CLIENT-ONLY.
 */
public final class ArcadeKeys {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("neroarcade", "arcade"));

    public static final KeyMapping UP = key("up", InputConstants.KEY_W);
    public static final KeyMapping DOWN = key("down", InputConstants.KEY_S);
    public static final KeyMapping LEFT = key("left", InputConstants.KEY_A);
    public static final KeyMapping RIGHT = key("right", InputConstants.KEY_D);
    public static final KeyMapping BUTTON_A = key("a", InputConstants.KEY_SPACE);
    public static final KeyMapping BUTTON_B = key("b", InputConstants.KEY_LSHIFT);
    public static final KeyMapping AIM_UP = key("aim_up", InputConstants.KEY_UP);
    public static final KeyMapping AIM_DOWN = key("aim_down", InputConstants.KEY_DOWN);
    public static final KeyMapping AIM_LEFT = key("aim_left", InputConstants.KEY_LEFT);
    public static final KeyMapping AIM_RIGHT = key("aim_right", InputConstants.KEY_RIGHT);

    /** In mask-bit order: up, down, left, right, A, B, aim up, aim down, aim left, aim right. */
    private static final List<KeyMapping> ALL = List.of(UP, DOWN, LEFT, RIGHT, BUTTON_A, BUTTON_B, AIM_UP, AIM_DOWN,
            AIM_LEFT, AIM_RIGHT);

    private static int screenHeld;

    private ArcadeKeys() {
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping("key.neroarcade." + name, keyCode, CATEGORY);
    }

    /** Every mapping, for the loaders' registration events. */
    public static List<KeyMapping> all() {
        return ALL;
    }

    /** The mask of mappings held in the world (no screen open). */
    public static int worldMask() {
        int mask = 0;
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).isDown()) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** The mask tracked from screen key events (bench test play). */
    public static int screenMask() {
        return screenHeld;
    }

    /**
     * Tracks a key event from a screen. Returns true when it is an arcade key (the screen should consume it).
     * Never matches Escape (Esc always closes the screen).
     */
    public static boolean screenKey(KeyEvent event, boolean down) {
        if (event.isEscape()) {
            return false;
        }
        boolean matched = false;
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).matches(event)) {
                screenHeld = down ? screenHeld | (1 << i) : screenHeld & ~(1 << i);
                matched = true;
            }
        }
        return matched;
    }

    /** Releases every screen-tracked key (the screen closed or lost focus). */
    public static void releaseScreenKeys() {
        screenHeld = 0;
    }

    /** The seat input for a held mask ({@link InputSender#toInput}). */
    public static SeatInput toInput(int mask, BusPhase phase) {
        return InputSender.toInput(mask, phase);
    }
}
