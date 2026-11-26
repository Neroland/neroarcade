package za.co.neroland.neroarcade.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
*///?}

//? if >=1.20.5 {
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;
//?}

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.StoredCartridge;

/**
 * Item data components through Neroland Core's {@link RegistrationProvider} (NeroNotes precedent: persistent
 * codec only; network sync falls back to it). Registered first in init step 3, before the items that use them.
 */
public final class ModDataComponents {

    //? if >=1.20.5 {
    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroArcadeCommon.MOD_ID);

    /** A module chip's module id ({@code neroarcade:pawn/cursor}). No player data. */
    private static final RegistryEntry<DataComponentType<Identifier>> MODULE_ID =
            COMPONENTS.register("module_id", key -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .build());

    /**
     * A cartridge's game, kept as canonical NBT ({@link StoredCartridge}; read through the validator). Carries
     * the author UUID (erasure only) and an opt-in credit string.
     */
    private static final RegistryEntry<DataComponentType<StoredCartridge>> GAME_DEF =
            COMPONENTS.register("game_def", key -> DataComponentType.<StoredCartridge>builder()
                    .persistent(StoredCartridge.CODEC)
                    .build());

    //?} else {
    /*private static final String MODULE_ID = "neroarcade:module_id";
    private static final String GAME_DEF = "neroarcade:game_def";
    *///?}

    private ModDataComponents() {
    }

    public static void writeGame(ItemStack stack, StoredCartridge cartridge) {
        //? if >=1.20.5 {
        stack.set(GAME_DEF.get(), cartridge);
        //?} else {
        /*stack.getOrCreateTag().put(GAME_DEF, cartridge.tag());
        *///?}
    }

    public static @Nullable StoredCartridge readGame(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(GAME_DEF.get());
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(GAME_DEF, Tag.TAG_COMPOUND)
                ? new StoredCartridge(tag.getCompound(GAME_DEF)) : null;
        *///?}
    }

    public static void writeModule(ItemStack stack, Identifier module) {
        //? if >=1.20.5 {
        stack.set(MODULE_ID.get(), module);
        //?} else {
        /*stack.getOrCreateTag().putString(MODULE_ID, module.toString());
        *///?}
    }

    public static @Nullable Identifier readModule(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(MODULE_ID.get());
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(MODULE_ID, Tag.TAG_STRING)
                ? Identifier.tryParse(tag.getString(MODULE_ID)) : null;
        *///?}
    }

    public static void init() {
        //? if <1.20.5 {
        /*LegacyChipRecipe.init();
        *///?}
    }
}
