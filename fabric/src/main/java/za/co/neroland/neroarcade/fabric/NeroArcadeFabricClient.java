package za.co.neroland.neroarcade.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.client.ArcadeClient;
import za.co.neroland.neroarcade.client.GalleryCapture;
import za.co.neroland.neroarcade.client.hud.ArcadeHud;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.render.ArcadeCabinetRenderer;
import za.co.neroland.neroarcade.client.render.DuplicatorRenderer;
import za.co.neroland.neroarcade.client.render.ExchangeRenderer;
import za.co.neroland.neroarcade.client.render.GameBenchRenderer;
import za.co.neroland.neroarcade.client.render.MarqueeScreenRenderer;
import za.co.neroland.neroarcade.client.screen.DuplicatorScreen;
import za.co.neroland.neroarcade.client.screen.ExchangeScreen;
import za.co.neroland.neroarcade.client.screen.GameBenchScreen;
import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModMenus;

/**
 * Fabric client entry point for NeroArcade: clientbound receivers, the Game Bench screen, the cabinet screen
 * renderer, the seated-player HUD element, the arcade key mappings, the client tick and the disconnect reset.
 */
public final class NeroArcadeFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroArcadeCommon.LOGGER.info("[NeroArcade] Fabric client bootstrap");
        // Clientbound receivers (client-only API; the main entry point already registered the types).
        FabricArcadeNetwork.registerClient();
        ArcadeClient.init();
        MenuScreens.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new);
        MenuScreens.register(ModMenus.CARTRIDGE_DUPLICATOR.get(), DuplicatorScreen::new);
        MenuScreens.register(ModMenus.ARCADE_EXCHANGE.get(), ExchangeScreen::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.GAME_BENCH.get(), ctx -> new GameBenchRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.CARTRIDGE_DUPLICATOR.get(), ctx -> new DuplicatorRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.ARCADE_EXCHANGE.get(), ctx -> new ExchangeRenderer());
        // the client-only /nsarcgallery capture and /nsarcber
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                GalleryCapture.registerClientCommands(dispatcher));
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                (graphics, delta) -> ArcadeHud.render(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, delta) -> ArcadeHud.render(graphics));
        *///?}
        //? if >=26.1 {
        ArcadeKeys.all().forEach(KeyMappingHelper::registerKeyMapping);
        //?} else {
        /*ArcadeKeys.all().forEach(KeyBindingHelper::registerKeyBinding);
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ArcadeClient.tick());
        // Before 1.21.4 item tints come from item colour providers (no item model definitions).
        //? if <1.21.4 {
        /*net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                za.co.neroland.neroarcade.client.ArcadeItemColours::colour,
                za.co.neroland.neroarcade.registry.ModItems.CARTRIDGE.get(),
                za.co.neroland.neroarcade.registry.ModItems.MODULE_CHIP.get(),
                za.co.neroland.neroarcade.registry.ModItems.BLANK_CARTRIDGE.get(),
                za.co.neroland.neroarcade.registry.ModItems.BLANK_CHIP.get());
        *///?}
        // A new session starts from a clean client mirror.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ArcadeNetwork.clearClientCaches());
    }
}
