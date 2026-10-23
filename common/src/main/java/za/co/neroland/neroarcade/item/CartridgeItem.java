package za.co.neroland.neroarcade.item;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.registry.ModDataComponents;
import za.co.neroland.neroarcade.registry.ModItems;

/**
 * A written cartridge: one game as data in the {@code neroarcade:game_def} component ({@link StoredCartridge}).
 * The item name is the game's title; the band is tinted with the theme's signature colour. The tooltip shows the
 * title, the credit ("Anonymous builder" unless the author opted in), the revision and the ten slots. The author
 * UUID is stored for erasure only and is never shown.
 */
public class CartridgeItem extends Item {

    public CartridgeItem(Item.Properties properties) {
        super(properties);
    }

    /** A cartridge stack for {@code def}; refuses one over the size cap. */
    public static ItemStack create(GameDef def) throws ValidationError {
        ItemStack stack = new ItemStack(ModItems.CARTRIDGE.get());
        write(stack, def);
        return stack;
    }

    /** Writes {@code def} into an existing cartridge stack (component + tint). */
    public static void write(ItemStack stack, GameDef def) throws ValidationError {
        stack.set(ModDataComponents.GAME_DEF.get(), StoredCartridge.of(def));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(), List.of(CartridgeTint.of(def))));
    }

    /** The stored cartridge on a stack, or null for anything that is not a written cartridge. */
    public static @Nullable StoredCartridge storedOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.CARTRIDGE.get())) {
            return null;
        }
        return stack.get(ModDataComponents.GAME_DEF.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        StoredCartridge stored = stack.get(ModDataComponents.GAME_DEF.get());
        if (stored != null) {
            String title = stored.rawTitle();
            if (!title.isEmpty()) {
                return Component.literal(title);
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        StoredCartridge stored = stack.get(ModDataComponents.GAME_DEF.get());
        if (stored == null) {
            tooltip.accept(Component.translatable("item.neroarcade.cartridge.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        Optional<GameDef> decoded = stored.tryDecode();
        if (decoded.isEmpty() || stored.error().isPresent()) {
            ValidationError error = stored.error().orElse(null);
            tooltip.accept(Component.translatable("neroarcade.cabinet.status.unreadable").withStyle(ChatFormatting.RED));
            if (error != null) {
                tooltip.accept(Component.translatable(error.langKey, error.args.toArray()).withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        GameDef def = decoded.get();
        tooltip.accept(def.authorCredit()
                .<Component>map(name -> Component.translatable("item.neroarcade.cartridge.by", name)
                        .withStyle(ChatFormatting.GRAY))
                .orElseGet(() -> Component.translatable("item.neroarcade.cartridge.anonymous")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        tooltip.accept(Component.translatable("item.neroarcade.cartridge.revision", def.revision())
                .withStyle(ChatFormatting.DARK_GRAY));
        CartridgeTooltips.remixLine(def).ifPresent(tooltip);
        for (ModuleType type : ModuleType.values()) {
            tooltip.accept(slotLine(def, type));
        }
    }

    /** "■ Pawn: Cursor" in the slot colour, or "■ Pawn: (default)" in grey. */
    public static MutableComponent slotLine(GameDef def, ModuleType type) {
        List<SlotEntry> entries = def.slot(type);
        MutableComponent chips = Component.empty();
        if (entries.isEmpty()) {
            chips.append(Component.translatable("item.neroarcade.cartridge.default").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    chips.append(Component.literal(", "));
                }
                SlotEntry entry = entries.get(i);
                chips.append(ModuleRegistry.get(entry.id())
                        .<Component>map(m -> Component.translatable(m.langKey()))
                        .orElseGet(() -> Component.literal(entry.id().toString())).copy().withStyle(ChatFormatting.WHITE));
            }
        }
        return Component.literal("■ ").append(Component.translatable(type.langKey())).append(": ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(type.colour)))
                .append(chips);
    }
}
