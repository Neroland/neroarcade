package za.co.neroland.neroarcade.item;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValue;

/**
 * A module chip: a template the Game Bench reads, never consumed. Its name is the module's name; the tooltip
 * is the slot (colour + word), the module's one-line description and each property with its default.
 */
public class ModuleChipItem extends Item {

    public ModuleChipItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<ArcadeModule> module = ModuleChips.moduleOf(stack);
        return module.<Component>map(m -> Component.translatable(m.langKey()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Optional<ArcadeModule> found = ModuleChips.moduleOf(stack);
        if (found.isEmpty()) {
            tooltip.accept(Component.translatable("item.neroarcade.module_chip.unknown").withStyle(ChatFormatting.GRAY));
            return;
        }
        ArcadeModule module = found.get();
        int colour = module.type().colour;
        tooltip.accept(Component.literal("■ ").append(Component.translatable(module.type().langKey()))
                .withStyle(style -> style.withColor(TextColor.fromRgb(colour))));
        tooltip.accept(Component.translatable(module.descLangKey()).withStyle(ChatFormatting.GRAY));
        for (PropertySpec spec : module.properties()) {
            tooltip.accept(Component.translatable("item.neroarcade.module_chip.property",
                    Component.translatable(spec.langKey(module)), valueText(module, spec, spec.defaultValue()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (module.type().stacks()) {
            tooltip.accept(Component.translatable("item.neroarcade.module_chip.stacks").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A property value as tooltip text: a number, on/off, or the enum constant's name. */
    public static MutableComponent valueText(ArcadeModule module, PropertySpec spec, PropertyValue value) {
        return switch (value) {
            case PropertyValue.IntValue i -> Component.literal(Integer.toString(i.value()));
            case PropertyValue.BoolValue b -> Component.translatable(b.value() ? "neroarcade.value.on" : "neroarcade.value.off");
            case PropertyValue.EnumValue e -> Component.translatable(spec.constantLangKey(module, e.constant()));
        };
    }
}
