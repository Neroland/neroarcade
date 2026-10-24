package za.co.neroland.neroarcade.client.render;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.screen.ScreenPixels;
import za.co.neroland.neroarcade.client.screen.ScreenRenderer;
import za.co.neroland.neroarcade.client.screen.ThemePalettes;

/**
 * One 128x128 dynamic texture showing a {@link ScreenRenderer} frame (the 64x64 logical screen at 2x, sampled
 * nearest-neighbour, scanlines optional). Created on first upload, registered with the texture manager under a
 * fixed id, re-uploaded only when the frame's checksum or the look changed. Render thread only. CLIENT-ONLY.
 */
public final class ScreenTexture {

    private final Identifier id;
    private @Nullable DynamicTexture texture;
    private long lastChecksum;
    private ThemePalettes.@Nullable Theme lastTheme;
    private boolean lastScanlines;
    private int uploads;

    public ScreenTexture(Identifier id) {
        this.id = id;
    }

    public Identifier id() {
        return id;
    }

    /** Uploads the renderer's current frame if it differs from what the GPU has. */
    public void upload(ScreenRenderer renderer, ThemePalettes.Theme theme, boolean scanlines) {
        long checksum = renderer.checksum();
        DynamicTexture current = texture;
        if (current != null && checksum == lastChecksum && theme == lastTheme && scanlines == lastScanlines) {
            return;
        }
        if (current == null) {
            current = new DynamicTexture(id::toString, ScreenPixels.TEXTURE, ScreenPixels.TEXTURE, false);
            Minecraft.getInstance().getTextureManager().register(id, current);
            texture = current;
        }
        NativeImage image = current.getPixels();
        byte[] pixels = renderer.pixels();
        for (int ty = 0; ty < ScreenPixels.TEXTURE; ty++) {
            for (int tx = 0; tx < ScreenPixels.TEXTURE; tx++) {
                image.setPixel(tx, ty, ScreenPixels.texel(pixels, theme, scanlines, tx, ty));
            }
        }
        current.upload();
        uploads++;
        ArcadeBerStats.upload();
        lastChecksum = checksum;
        lastTheme = theme;
        lastScanlines = scanlines;
    }

    /** True once something was uploaded. */
    public boolean ready() {
        return texture != null;
    }

    /** Uploads so far (the budget readout). */
    public int uploads() {
        return uploads;
    }

    /** Forces the next upload (a new screen took this texture). */
    public void invalidate() {
        lastTheme = null;
    }

    /** Frees the GPU texture. */
    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
        lastTheme = null;
    }
}
