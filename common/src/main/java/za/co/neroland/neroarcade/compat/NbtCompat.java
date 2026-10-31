package za.co.neroland.neroarcade.compat;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//? if <1.21.5 {
/*import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
*///?}

/**
 * Version seam for reading NBT. 1.21.5 made the {@link CompoundTag} / {@link ListTag} getters return
 * {@link Optional} (and added {@code getIntOr}-style defaults); the older lines return a zero value for a missing or
 * mistyped key. NeroArcade's saved stores read through here, so a missing key reads the same on every line.
 */
public final class NbtCompat {

    private NbtCompat() {
    }

    public static Optional<String> getString(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getString(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
        *///?}
    }

    public static Optional<Integer> getInt(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getInt(key);
        //?} else {
        /*return tag.get(key) instanceof NumericTag n ? Optional.of(n.getAsInt()) : Optional.empty();
        *///?}
    }

    public static Optional<Long> getLong(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getLong(key);
        //?} else {
        /*return tag.get(key) instanceof NumericTag n ? Optional.of(n.getAsLong()) : Optional.empty();
        *///?}
    }

    public static Optional<ListTag> getList(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getList(key);
        //?} else {
        /*return tag.get(key) instanceof ListTag list ? Optional.of(list) : Optional.empty();
        *///?}
    }

    public static Optional<CompoundTag> getCompound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getCompound(key);
        //?} else {
        /*return tag.get(key) instanceof CompoundTag c ? Optional.of(c) : Optional.empty();
        *///?}
    }

    /** The compound under {@code key}, or a fresh empty one (not attached) when there is none. */
    public static CompoundTag getCompoundOrEmpty(CompoundTag tag, String key) {
        return getCompound(tag, key).orElseGet(CompoundTag::new);
    }

    public static Optional<CompoundTag> getCompound(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getCompound(index);
        //?} else {
        /*return index >= 0 && index < list.size() && list.get(index) instanceof CompoundTag c ? Optional.of(c)
                : Optional.empty();
        *///?}
    }

    public static Optional<String> getString(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getString(index);
        //?} else {
        /*return index >= 0 && index < list.size() && list.get(index) instanceof StringTag s
                ? Optional.of(s.getAsString()) : Optional.empty();
        *///?}
    }

    public static String getStringOr(CompoundTag tag, String key, String fallback) {
        return getString(tag, key).orElse(fallback);
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        return getInt(tag, key).orElse(fallback);
    }

    public static long getLongOr(CompoundTag tag, String key, long fallback) {
        return getLong(tag, key).orElse(fallback);
    }
}
