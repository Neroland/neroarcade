package za.co.neroland.neroarcade.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.client.ArcadeClient;
import za.co.neroland.neroarcade.client.hud.ArcadeHud;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.render.ArcadeCabinetRenderer;
import za.co.neroland.neroarcade.client.render.MarqueeScreenRenderer;
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
        BlockEntityRendererRegistry.register(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                (graphics, delta) -> ArcadeHud.render(graphics));
        ArcadeKeys.all().forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ArcadeClient.tick());
        // A new session starts from a clean client mirror.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ArcadeNetwork.clearClientCaches());
    }
}
