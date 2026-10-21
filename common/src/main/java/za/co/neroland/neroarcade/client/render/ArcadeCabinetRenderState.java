package za.co.neroland.neroarcade.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/** What the cabinet renderer draws this frame: the screen texture (null = nothing) and the facing. */
public class ArcadeCabinetRenderState extends BlockEntityRenderState {

    /** Facing as quarter turns clockwise from north (the blockstate's y / 90). */
    public int quarterTurns;
    public @Nullable Identifier screen;
}
