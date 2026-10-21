package za.co.neroland.neroarcade.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * NeroArcade's own creative tab, registered cross-loader through Neroland Core's {@link RegistrationProvider} over
 * the vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod owns its tab; nothing is added to Core's
 * "Neroland" tab, which lists Core's own items only.
 *
 * <p>Order: the blocks, the blank cartridge and blank chip, then one chip per registered module in slot order
 * (from the frozen {@link ModuleRegistry}). The written cartridge is not listed: it only exists once a bench
 * saves a game. The icon is a cartridge.
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroArcadeCommon.MOD_ID);

    /** Item suppliers in display order, drained into the tab when it is built. */
    private static final List<Supplier<? extends ItemLike>> CONTENTS = new ArrayList<>();

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROARCADE = TABS.register("neroarcade",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.neroarcade"))
                    .icon(() -> new ItemStack(ModItems.CARTRIDGE.get()))
                    .displayItems((params, output) -> {
                        CONTENTS.forEach(s -> output.accept(s.get()));
                        for (ModuleType type : ModuleType.values()) {
                            for (ArcadeModule module : ModuleRegistry.byType(type)) {
                                output.accept(ModuleChips.stackFor(module));
                            }
                        }
                    })
                    .build());

    private ModCreativeTab() {
    }

    /** Append an item to the NeroArcade tab. Call during common init, before tabs are built. */
    public static void add(Supplier<? extends ItemLike> item) {
        CONTENTS.add(item);
    }

    /** Queues NeroArcade's own items and forces the tab registration (common init step 3, last). */
    public static void init() {
        if (!CONTENTS.isEmpty()) {
            return;
        }
        add(ModItems.ARCADE_CABINET);
        add(ModItems.GAME_BENCH);
        add(ModItems.CARTRIDGE_DUPLICATOR);
        add(ModItems.ARCADE_EXCHANGE);
        add(ModItems.MARQUEE_SCREEN);
        add(ModItems.LINK_BUS);
        add(ModItems.LINK_CABLE);
        add(ModItems.BLANK_CARTRIDGE);
        add(ModItems.BLANK_CHIP);
    }
}
