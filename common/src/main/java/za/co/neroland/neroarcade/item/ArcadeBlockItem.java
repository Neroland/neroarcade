package za.co.neroland.neroarcade.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The item form of a NeroArcade block, carrying the block's tooltip ({@code block.neroarcade.<id>.tooltip}).
 * A {@code BlockItem} subclass because 26.x {@code Block} has no {@code appendHoverText}.
 */
public class ArcadeBlockItem extends BlockItem {

    private final String tooltipKey;

    public ArcadeBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
