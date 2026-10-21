package za.co.neroland.neroarcade.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/** The 23 sound events of {@code sounds.json} (004 "Sound event ids"), registered as variable-range events. */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroArcadeCommon.MOD_ID);

    private static final Map<String, RegistryEntry<SoundEvent>> BY_KEY = new LinkedHashMap<>();

    static {
        for (String key : ArcadeIds.SOUNDS) {
            BY_KEY.put(key, SOUNDS.register(key, k -> SoundEvent.createVariableRangeEvent(k.identifier())));
        }
    }

    public static final RegistryEntry<SoundEvent> CABINET_INSERT = BY_KEY.get("cabinet.insert");
    public static final RegistryEntry<SoundEvent> CABINET_START = BY_KEY.get("cabinet.start");
    public static final RegistryEntry<SoundEvent> CABINET_DENY = BY_KEY.get("cabinet.deny");
    public static final RegistryEntry<SoundEvent> CABINET_WIN = BY_KEY.get("cabinet.win");
    public static final RegistryEntry<SoundEvent> CABINET_LOSE = BY_KEY.get("cabinet.lose");
    public static final RegistryEntry<SoundEvent> BENCH_SAVE = BY_KEY.get("bench.save");

    private ModSounds() {
    }

    /** The registered event for a {@code sounds.json} key (e.g. {@code theme.hangar.loop}), or null. */
    public static RegistryEntry<SoundEvent> get(String key) {
        return BY_KEY.get(key);
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
