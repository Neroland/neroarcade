package za.co.neroland.neroarcade.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//? if >=1.20.5 {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?} else {
/*import net.neoforged.neoforge.event.TickEvent;
*///?}

import za.co.neroland.neroarcade.command.ArcadeCommands;
import za.co.neroland.neroarcade.server.ServerHooks;

/** NeoForge server wiring: the {@code /neroarcade} commands and the {@link ServerHooks} lifecycle events. */
public final class NeoForgeArcadeEvents {

    private NeoForgeArcadeEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ArcadeCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerHooks.fireStopping(event.getServer()));
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ServerHooks.fireTick(event.getServer()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ServerHooks.fireTick(event.getServer());
            }
        });
        *///?}
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
}
