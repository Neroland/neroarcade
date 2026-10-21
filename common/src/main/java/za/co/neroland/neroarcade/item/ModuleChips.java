package za.co.neroland.neroarcade.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.registry.ModDataComponents;
import za.co.neroland.neroarcade.registry.ModItems;

/**
 * Module chip stacks: one {@code neroarcade:module_chip} item, the chip named by the {@code neroarcade:module_id}
 * component, and the model chosen by {@code custom_model_data} (strings[0] = slot key, colors[0] = slot colour —
 * 004 "Item ids"). A stack without a known module id is a malformed chip and renders as the blank chip.
 */
public final class ModuleChips {

    private ModuleChips() {
    }

    /** The chip stack for a registered module. */
    public static ItemStack stackFor(ArcadeModule module) {
        ItemStack stack = new ItemStack(ModItems.MODULE_CHIP.get());
        stack.set(ModDataComponents.MODULE_ID.get(), module.id());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, modelData(module.type()));
        return stack;
    }

    /** {@code CustomModelData(strings=[slot key], colors=[slot colour])}. */
    public static CustomModelData modelData(ModuleType type) {
        return new CustomModelData(List.of(), List.of(), List.of(type.key()), List.of(type.colour));
    }

    /** The module id a stack carries (any item; empty when it is not a chip or has no id). */
    public static Optional<Identifier> idOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.MODULE_CHIP.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(ModDataComponents.MODULE_ID.get()));
    }

    /** The registered module a chip stack names, if this server has it. */
    public static Optional<ArcadeModule> moduleOf(ItemStack stack) {
        return idOf(stack).flatMap(ModuleRegistry::get);
    }
}
