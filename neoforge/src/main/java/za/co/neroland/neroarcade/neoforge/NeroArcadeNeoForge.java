package za.co.neroland.neroarcade.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/** NeoForge entry point for NeroArcade. */
@Mod(NeroArcadeCommon.MOD_ID)
public final class NeroArcadeNeoForge {

    public NeroArcadeNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroArcadeCommon.LOGGER.info("[NeroArcade] NeoForge bootstrap");
        // Common init declares the payloads; the registrar listener below consumes them.
        NeroArcadeCommon.init();
        // Common init created NeroArcade's deferred registers through Core's registration seam; this
        // attaches them to our mod event bus.
        RegistrationProvider.attach(modEventBus);
        NeoForgeArcadeNetwork.register(modEventBus);
        NeoForgeArcadeEvents.register();
        // Client-only setup. Gated so client classes never load on a dedicated server.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus, modContainer);
        }
    }
}
