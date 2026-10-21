package za.co.neroland.neroarcade.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.client.ArcadeClient;
import za.co.neroland.neroarcade.client.hud.ArcadeHud;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.render.ArcadeCabinetRenderer;
import za.co.neroland.neroarcade.client.screen.GameBenchScreen;
import za.co.neroland.neroarcade.network.ArcadeNetwork;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModMenus;

/**
 * Forge client-only setup: the Game Bench screen, the cabinet screen renderer, the seated-player HUD layer, the
 * arcade key mappings, the client tick and the disconnect reset. Loaded only on the physical client (the entry
 * point gates on {@code Dist.CLIENT}), so client classes never reach a dedicated server.
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    /** Forge 26.x: each event class owns a static {@code BUS}; the client-setup event lives on the mod bus group. */
    public static void init(FMLJavaModLoadingContext context) {
        ArcadeClient.init();
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() ->
                MenuScreens.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new)));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
        });
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                    (g, delta) -> ArcadeHud.render(g));
        });
        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            ArcadeKeys.all().forEach(event::register);
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            ArcadeClient.tick();
        });
        // A new session starts from a clean client mirror.
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            ArcadeNetwork.clearClientCaches();
        });
    }
}
