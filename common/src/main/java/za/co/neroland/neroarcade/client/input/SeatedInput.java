package za.co.neroland.neroarcade.client.input;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import org.jetbrains.annotations.Nullable;

/**
 * The player's movement input while seated at a cabinet: nothing. Swapped into {@code LocalPlayer.input} while
 * seated (so W/A/S/D/Space/Shift steer the pawn and the player neither walks, jumps nor sneaks away), and the
 * original input is put back the moment the seat is gone. Mouse look stays, so the player can still look at the
 * screen. The loader-neutral alternative to a movement-input event (Fabric has none). CLIENT-ONLY.
 */
public final class SeatedInput extends ClientInput {

    private static @Nullable ClientInput original;
    private static @Nullable LocalPlayer owner;

    private SeatedInput() {
    }

    @Override
    public void tick() {
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }

    /** Applies or lifts the seat's hold on {@code player}'s movement. Idempotent; call every client tick. */
    public static void hold(@Nullable LocalPlayer player, boolean seated) {
        if (player == null) {
            original = null;
            owner = null;
            return;
        }
        if (seated) {
            if (!(player.input instanceof SeatedInput)) {
                original = player.input;
                owner = player;
                player.input = new SeatedInput();
            }
        } else if (player.input instanceof SeatedInput) {
            ClientInput back = owner == player ? original : null;
            player.input = back != null ? back : new net.minecraft.client.player.KeyboardInput(
                    net.minecraft.client.Minecraft.getInstance().options);
            original = null;
            owner = null;
        }
    }
}
