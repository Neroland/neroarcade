package za.co.neroland.neroarcade.client.input.compat;

// Look-alike of 1.21.9's net.minecraft.client.input.KeyEvent for the older lines, where screens receive the raw
// key code, scan code and modifiers (see legacyRenames). Compiled only below 1.21.9.
//? if <1.21.9 {
/*import com.mojang.blaze3d.platform.InputConstants;

public record KeyEvent(int key, int scancode, int modifiers) {

    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }

    public boolean isSelection() {
        return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE
                || key == InputConstants.KEY_NUMPADENTER;
    }

    public boolean isCycleFocus() {
        return key == InputConstants.KEY_TAB;
    }
}
*///?}
