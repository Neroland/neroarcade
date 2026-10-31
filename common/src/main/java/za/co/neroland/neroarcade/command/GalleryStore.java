package za.co.neroland.neroarcade.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * What each creative gallery placed, so {@code /neroarcade gallery clear} removes exactly that: every changed
 * position with the state it had before and the state the gallery put there. A clear puts the old state back only
 * where the gallery's own block still stands (a block a player changed since is left alone). Kept with the world
 * (overworld data storage through Core's {@code SavedDataRecovery}) so a clear still works after a restart; holds
 * positions and block states only — no player, no name, so nothing to erase.
 */
public final class GalleryStore extends SavedData {

    /** One changed position. */
    public record Change(long pos, BlockState before, BlockState placed) {

        static final Codec<Change> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("p").forGetter(Change::pos),
                BlockState.CODEC.fieldOf("b").forGetter(Change::before),
                BlockState.CODEC.fieldOf("a").forGetter(Change::placed)
        ).apply(i, Change::new));
    }

    /** One built gallery: its dimension, the operator's origin and every change, in placement order. */
    public record Gallery(String dimension, long origin, List<Change> changes, boolean exchangeSeeded) {

        static final Codec<Gallery> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Gallery::dimension),
                Codec.LONG.fieldOf("origin").forGetter(Gallery::origin),
                Change.CODEC.listOf().fieldOf("changes").forGetter(Gallery::changes),
                Codec.BOOL.optionalFieldOf("exchange", false).forGetter(Gallery::exchangeSeeded)
        ).apply(i, Gallery::new));

        public Gallery {
            changes = List.copyOf(changes);
        }
    }

    private static final Codec<GalleryStore> CODEC = Gallery.CODEC.listOf()
            .xmap(GalleryStore::new, store -> store.galleries);

    public static final SavedDataType<GalleryStore> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "gallery"), GalleryStore::new, CODEC);

    public static final String RECOVERY_NAME = "neroarcade:gallery";

    private final List<Gallery> galleries;

    public GalleryStore() {
        this(List.of());
    }

    private GalleryStore(List<Gallery> galleries) {
        this.galleries = new ArrayList<>(galleries);
    }

    public static GalleryStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, GalleryStore::new, RECOVERY_NAME);
    }

    public List<Gallery> galleries() {
        return List.copyOf(galleries);
    }

    public void add(Gallery gallery) {
        galleries.add(gallery);
        setDirty();
    }

    public void remove(Gallery gallery) {
        if (galleries.remove(gallery)) {
            setDirty();
        }
    }

    /** The gallery in {@code dimension} whose footprint holds {@code (x, y, z)}, if any. */
    public Optional<Gallery> at(String dimension, int x, int y, int z) {
        for (Gallery g : galleries) {
            if (!g.dimension().equals(dimension)) {
                continue;
            }
            int ox = net.minecraft.core.BlockPos.getX(g.origin());
            int oy = net.minecraft.core.BlockPos.getY(g.origin());
            int oz = net.minecraft.core.BlockPos.getZ(g.origin());
            if (GalleryLayout.inFootprint(x - ox, Math.max(0, y - oy), z - oz)
                    || (x == ox && z == oz && Math.abs(y - oy) <= 3)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    /** Whether any recorded gallery placed a block at {@code pos} in {@code dimension}. */
    public boolean placedAt(String dimension, long pos) {
        for (Gallery g : galleries) {
            if (g.dimension().equals(dimension)) {
                for (Change c : g.changes()) {
                    if (c.pos() == pos) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
