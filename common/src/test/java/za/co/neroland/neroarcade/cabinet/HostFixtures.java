package za.co.neroland.neroarcade.cabinet;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;

/** Shared cabinet-test setup: a 10-second default game and a counting board writer. */
final class HostFixtures {

    static final BlockPos POS = new BlockPos(10, 64, -3);
    static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    /** Ticks to finish the 10-second game after the countdown, with slack. */
    static final int GAME_TICKS = 10 * 20 + 40;

    private HostFixtures() {
    }

    /** Default chips with survive_time set to 10 seconds (no hazard: won by the timer). */
    static ResolvedGame tenSecondGame() {
        TestModules.installShipped();
        Map<ModuleType, List<SlotEntry>> slots = new EnumMap<>(ModuleType.class);
        slots.put(ModuleType.RULE, List.of(new SlotEntry(ModuleDefaults.RULE,
                Map.of("seconds", new PropertyValue.IntValue(10)))));
        GameDef def = TestModules.def(slots);
        try {
            return CartridgeValidator.validate(def);
        } catch (ValidationError e) {
            throw new AssertionError(e);
        }
    }

    static final class CountingWriter implements BoardWriter {
        int calls;
        BusMatchResult last;

        @Override
        public void record(BusMatchResult result) {
            calls++;
            last = result;
        }
    }

    static void tick(MatchHost host, FakeHostEnv env, int ticks) {
        for (int i = 0; i < ticks; i++) {
            env.time++;
            host.tick();
        }
    }
}
