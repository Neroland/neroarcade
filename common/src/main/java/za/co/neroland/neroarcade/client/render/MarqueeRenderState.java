package za.co.neroland.neroarcade.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/** What the marquee renderer draws this frame: the main square, the optional score strip, scale and facing. */
public class MarqueeRenderState extends BlockEntityRenderState {

    public int quarterTurns;
    public int scale = 1;
    /** The main square (host screen or top ten); null with {@link #idle} = the static card. */
    public @Nullable Identifier main;
    /** The score strip while a match is on, else null. */
    public @Nullable Identifier strip;
    /** No bus: draw the static "NERO ARCADE" card over the whole surface. */
    public boolean idle;
}
