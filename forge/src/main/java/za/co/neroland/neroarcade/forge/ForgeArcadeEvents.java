package za.co.neroland.neroarcade.forge;

import net.minecraft.server.level.ServerPlayer;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import za.co.neroland.neroarcade.command.ArcadeCommands;
import za.co.neroland.neroarcade.server.ServerHooks;

/** Forge server wiring: the {@code /neroarcade} commands and the {@link ServerHooks} lifecycle events. */
public final class ForgeArcadeEvents {

    private ForgeArcadeEvents() {
    }

    /** Called once from the Forge entry point. Forge 26.x: each event class owns a static {@code BUS}. */
    public static void register() {
        //? if >=1.21.9 {
        RegisterCommandsEvent.BUS.addListener(event -> ArcadeCommands.register(event.getDispatcher()));
        ServerStoppingEvent.BUS.addListener(event -> ServerHooks.fireStopping(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ServerHooks.fireTick(event.server()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
        //?} else if >=1.21.6 {
        /*// Forge 1.21.6-1.21.8: the server-tick event has no server() accessor.
        RegisterCommandsEvent.BUS.addListener(event -> ArcadeCommands.register(event.getDispatcher()));
        ServerStoppingEvent.BUS.addListener(event -> ServerHooks.fireStopping(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ServerHooks.fireTick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
        *///?} else {
        /*// Before Forge's EventBus 7 (1.21.6) game events go through the global MinecraftForge.EVENT_BUS.
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ArcadeCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerHooks.fireStopping(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) ->
                ServerHooks.fireTick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
        *///?}
    }
}
