package za.co.neroland.neroarcade.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.command.ArcadeCommands;
import za.co.neroland.neroarcade.server.ServerHooks;

/** Fabric entry point for NeroArcade. */
public final class NeroArcadeFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroArcadeCommon.LOGGER.info("[NeroArcade] Fabric bootstrap");
        // Common init declares the payloads; the network registration below consumes them.
        NeroArcadeCommon.init();
        FabricArcadeNetwork.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ArcadeCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerHooks::fireStopping);
        ServerTickEvents.END_SERVER_TICK.register(ServerHooks::fireTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerHooks.firePlayerLeave(handler.player));
    }
}
