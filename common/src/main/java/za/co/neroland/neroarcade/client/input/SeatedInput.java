package za.co.neroland.neroarcade.client.input;

//? if >=1.21.2 {
import net.minecraft.client.player.ClientInput;
//?} else {
/*import net.minecraft.client.player.Input;
*///?}
import net.minecraft.client.player.LocalPlayer;
//? if >=1.21.2 {
import net.minecraft.world.entity.player.Input;
//?}
//? if >=1.21.5 {
import net.minecraft.world.phys.Vec2;
//?}

import org.jetbrains.annotations.Nullable;

/**
 * The player's movement input while seated at a cabinet: nothing. Swapped into {@code LocalPlayer.input} while
 * seated (so W/A/S/D/Space/Shift steer the pawn and the player neither walks, jumps nor sneaks away), and the
 * original input is put back the moment the seat is gone. Mouse look stays, so the player can still look at the
 * screen. The loader-neutral alternative to a movement-input event (Fabric has none). CLIENT-ONLY.
 */
//? if >=1.21.2 {
public final class SeatedInput extends ClientInput {
//?} else {
/*// Before 1.21.2 the player's movement input class is client.player.Input.
public final class SeatedInput extends Input {
*///?}

    //? if >=1.21.2 {
    private static @Nullable ClientInput original;
    //?} else {
    /*private static @Nullable Input original;
    *///?}
    private static @Nullable LocalPlayer owner;

    private SeatedInput() {
    }

    //? if >=1.21.5 {
    @Override
    public void tick() {
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void tick() {
        this.keyPresses = Input.EMPTY;
        this.leftImpulse = 0f;
        this.forwardImpulse = 0f;
    }
    *///?} else {
    /*@Override
    public void tick(boolean slowDown, float sneakSpeedModifier) {
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.jumping = false;
        this.shiftKeyDown = false;
        this.leftImpulse = 0f;
        this.forwardImpulse = 0f;
    }
    *///?}

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
            //? if >=1.21.2 {
            ClientInput back = owner == player ? original : null;
            //?} else {
            /*Input back = owner == player ? original : null;
            *///?}
            player.input = back != null ? back : new net.minecraft.client.player.KeyboardInput(
                    net.minecraft.client.Minecraft.getInstance().options);
            original = null;
            owner = null;
        }
    }
}
