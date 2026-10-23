package za.co.neroland.neroarcade.neoforge;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

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
 * NeoForge client-only setup: the Game Bench screen, the cabinet screen renderer, the seated-player HUD layer, the
 * arcade key mappings, the client tick and the disconnect reset. Loaded only on the physical client (the entry point
 * gates on {@code Dist.CLIENT}), so client classes never reach a dedicated server.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        ArcadeClient.init();
        modEventBus.addListener((RegisterMenuScreensEvent event) ->
                event.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
        });
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"), (g, delta) -> ArcadeHud.render(g)));
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> ArcadeKeys.all().forEach(event::register));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ArcadeClient.tick());
        // A new session starts from a clean client mirror.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ArcadeNetwork.clearClientCaches());
    }
}
