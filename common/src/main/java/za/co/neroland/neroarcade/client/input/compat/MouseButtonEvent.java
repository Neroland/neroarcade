package za.co.neroland.neroarcade.client.input.compat;

// Look-alike of 1.21.9's net.minecraft.client.input.MouseButtonEvent for the older lines, where screens and widgets
// receive the raw position and button (see legacyRenames). Compiled only below 1.21.9.
//? if <1.21.9 {
/*public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) {

    public MouseButtonEvent(double x, double y, int button) {
        this(x, y, new MouseButtonInfo(button, 0));
    }

    public int button() {
        return buttonInfo.button();
    }
}
*///?}
