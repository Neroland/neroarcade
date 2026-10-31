package za.co.neroland.neroarcade.forge;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

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
        //? if >=1.21.9 {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new);
            MenuScreens.register(ModMenus.CARTRIDGE_DUPLICATOR.get(), DuplicatorScreen::new);
            MenuScreens.register(ModMenus.ARCADE_EXCHANGE.get(), ExchangeScreen::new);
        }));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.GAME_BENCH.get(), ctx -> new GameBenchRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.CARTRIDGE_DUPLICATOR.get(), ctx -> new DuplicatorRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_EXCHANGE.get(), ctx -> new ExchangeRenderer());
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
        // the client-only /nsarcgallery capture and /nsarcber
        RegisterClientCommandsEvent.BUS.addListener(event -> {
            GalleryCapture.registerClientCommands(event.getDispatcher());
        });
        // A new session starts from a clean client mirror.
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            ArcadeNetwork.clearClientCaches();
        });
        //?} else if >=1.21.6 {
        /*// Forge 1.21.6-1.21.8: the mod-bus events are reached through getBus(modBusGroup).
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new);
            MenuScreens.register(ModMenus.CARTRIDGE_DUPLICATOR.get(), DuplicatorScreen::new);
            MenuScreens.register(ModMenus.ARCADE_EXCHANGE.get(), ExchangeScreen::new);
        }));
        EntityRenderersEvent.RegisterRenderers.getBus(context.getModBusGroup()).addListener(event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.GAME_BENCH.get(), ctx -> new GameBenchRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.CARTRIDGE_DUPLICATOR.get(), ctx -> new DuplicatorRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_EXCHANGE.get(), ctx -> new ExchangeRenderer());
        });
        AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(event -> {
            event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                    (g, delta) -> ArcadeHud.render(g));
        });
        RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(event -> {
            ArcadeKeys.all().forEach(event::register);
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            ArcadeClient.tick();
        });
        // the client-only /nsarcgallery capture and /nsarcber
        RegisterClientCommandsEvent.BUS.addListener(event -> {
            GalleryCapture.registerClientCommands(event.getDispatcher());
        });
        // A new session starts from a clean client mirror.
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            ArcadeNetwork.clearClientCaches();
        });
        *///?} else {
        /*// Before Forge's EventBus 7 (1.21.6): mod-lifecycle events on the mod's own event bus, game events on
        // the global MinecraftForge.EVENT_BUS.
        IEventBus modBus = context.getModEventBus();
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.GAME_BENCH.get(), GameBenchScreen::new);
            MenuScreens.register(ModMenus.CARTRIDGE_DUPLICATOR.get(), DuplicatorScreen::new);
            MenuScreens.register(ModMenus.ARCADE_EXCHANGE.get(), ExchangeScreen::new);
        }));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_CABINET.get(), ctx -> new ArcadeCabinetRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MARQUEE_SCREEN.get(), ctx -> new MarqueeScreenRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.GAME_BENCH.get(), ctx -> new GameBenchRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.CARTRIDGE_DUPLICATOR.get(), ctx -> new DuplicatorRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.ARCADE_EXCHANGE.get(), ctx -> new ExchangeRenderer());
        });
        modBus.addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                        (g, delta) -> ArcadeHud.render(g)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> ArcadeKeys.all().forEach(event::register));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> ArcadeClient.tick());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                GalleryCapture.registerClientCommands(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ArcadeNetwork.clearClientCaches());
        *///?}
        // Before 1.21.4 item tints come from item colour handlers (no item model definitions).
        //? if <1.21.4 {
        /*context.getModEventBus().addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) ->
                event.register(za.co.neroland.neroarcade.client.ArcadeItemColours::colour,
                za.co.neroland.neroarcade.registry.ModItems.CARTRIDGE.get(),
                za.co.neroland.neroarcade.registry.ModItems.MODULE_CHIP.get(),
                za.co.neroland.neroarcade.registry.ModItems.BLANK_CARTRIDGE.get(),
                za.co.neroland.neroarcade.registry.ModItems.BLANK_CHIP.get()));
        *///?}
    }
}
