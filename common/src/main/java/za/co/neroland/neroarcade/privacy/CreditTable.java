package za.co.neroland.neroarcade.privacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import za.co.neroland.neroarcade.compat.NbtCompat;
import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * The credit opt-in flags (003 §6.3), pure: player UUID → on. Absent = off, and "off" is never stored — turning
 * credit off removes the row, so the store only ever lists players who asked to be named.
 */
public final class CreditTable {

    private static final String KEY_ON = "on";

    private final Map<UUID, Boolean> flags = new LinkedHashMap<>();

    /** Off unless the player turned it on. */
    public boolean isOn(UUID player) {
        return player != null && flags.getOrDefault(player, Boolean.FALSE);
    }

    /** @return true if the flag changed */
    public boolean set(UUID player, boolean on) {
        if (player == null || Tombstones.isTombstone(player)) {
            return false;
        }
        if (on) {
            return flags.put(player, Boolean.TRUE) == null;
        }
        return flags.remove(player) != null;
    }

    public boolean erase(UUID player) {
        return flags.remove(player) != null;
    }

    public boolean holds(UUID player) {
        return flags.containsKey(player);
    }

    public int size() {
        return flags.size();
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        flags.forEach((player, on) -> {
            if (on) {
                CompoundTag row = new CompoundTag();
                row.putString("uuid", player.toString());
                list.add(row);
            }
        });
        root.put(KEY_ON, list);
        return root;
    }

    public static CreditTable load(CompoundTag root) {
        CreditTable table = new CreditTable();
        Optional<ListTag> list = NbtCompat.getList(root, KEY_ON);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            try {
                CompoundTag row = NbtCompat.getCompound(list.get(), i).orElseThrow();
                table.flags.put(UUID.fromString(NbtCompat.getString(row, "uuid").orElseThrow()), Boolean.TRUE);
            } catch (RuntimeException malformed) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] skipping malformed credit row {} ({})", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }
}
