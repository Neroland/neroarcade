package za.co.neroland.neroarcade.module.hook;

/**
 * A seat's input after the CONTROL chip has interpreted the raw bitfield. Integer-only.
 *
 * @param moveX    -1, 0 or 1 (right is +1)
 * @param moveY    -1, 0 or 1 (down is +1; row 0 is the top of the screen)
 * @param aimX     -127..127 (twin stick) or 0
 * @param aimY     -127..127 (twin stick) or 0
 * @param a        action button A held this tick (level)
 * @param b        action button B held this tick (level)
 * @param aPressed A went down this tick (edge)
 * @param bPressed B went down this tick (edge)
 */
public record Intent(int moveX, int moveY, int aimX, int aimY, boolean a, boolean b, boolean aPressed,
        boolean bPressed) {

    public static final Intent NONE = new Intent(0, 0, 0, 0, false, false, false, false);

    public Intent {
        moveX = Integer.signum(moveX);
        moveY = Integer.signum(moveY);
        aimX = Math.max(-127, Math.min(127, aimX));
        aimY = Math.max(-127, Math.min(127, aimY));
    }
}
