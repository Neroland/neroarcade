package za.co.neroland.neroarcade.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Version seam for messages to one player. 26.1 split {@code Player.displayClientMessage(Component, boolean)} into
 * {@code sendOverlayMessage} (the action bar) and {@code sendSystemMessage} (chat); the 1.21.x lines only have the
 * older method. Every NeroArcade call site goes through here, so the difference stays in one place.
 */
public final class PlayerMessages {

    private PlayerMessages() {
    }

    /** Shows {@code message} on {@code player}'s action bar. */
    public static void overlay(Player player, Component message) {
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);
        *///?}
    }

    /** Adds {@code message} to {@code player}'s chat. */
    public static void chat(Player player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }
}
