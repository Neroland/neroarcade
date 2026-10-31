package za.co.neroland.neroarcade.client.screen.compat;

// Before 26.1 a container screen draws its panel in renderBg(..) and AbstractContainerScreen.render(..) then draws
// the slots, labels and widgets on top; 26.1 replaced that with extractContents(..), which the NeroArcade screens
// override (panel, then super.extractContents for the slots, then any overlay text). On older lines the build
// points the screens at this base (see legacyRenames): their extractContents(..) runs as the background pass, the
// inherited super.extractContents(..) is a no-op, and render(..) adds the hovered-slot tooltip, which the older
// AbstractContainerScreen.render(..) does not draw itself. Compiled only below 26.1.
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class LegacyContainerScreen<T extends AbstractContainerMenu>
        extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<T> {

    protected LegacyContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.inventoryLabelY = height - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        extractContents(g, mouseX, mouseY, partialTick);
    }

    // The screens' panel pass; their call to the super version draws nothing here (render(..) draws the slots).
    public void extractContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
}
*///?}
