package za.co.neroland.neroarcade.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/** MinecraftForge entry point for NeroArcade. */
@Mod(NeroArcadeCommon.MOD_ID)
public final class NeroArcadeForge {

    public NeroArcadeForge(FMLJavaModLoadingContext context) {
        NeroArcadeCommon.LOGGER.info("[NeroArcade] Forge bootstrap");
        // Common init declares the payloads; the channel below is sealed the moment it is built, so
        // that ordering is mandatory on Forge.
        NeroArcadeCommon.init();
        // Common init created NeroArcade's deferred registers through Core's registration seam; this
        // attaches them to our mod bus group.
        //? if >=1.21.6 {
        RegistrationProvider.attach(context.getModBusGroup());
        //?} else {
        /*RegistrationProvider.attach(context.getModEventBus());
        *///?}
        ForgeArcadeNetwork.register();
        ForgeArcadeEvents.register();
        // Client-only setup. Gated so client classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(context);
        }
    }
}
