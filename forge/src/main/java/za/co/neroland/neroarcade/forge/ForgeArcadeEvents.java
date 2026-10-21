package za.co.neroland.neroarcade.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
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
        RegisterCommandsEvent.BUS.addListener(event -> ArcadeCommands.register(event.getDispatcher()));
        ServerStoppingEvent.BUS.addListener(event -> ServerHooks.fireStopping(event.getServer()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
}
