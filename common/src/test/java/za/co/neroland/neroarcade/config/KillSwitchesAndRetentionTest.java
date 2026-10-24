package za.co.neroland.neroarcade.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.bus.BusTopology;
import za.co.neroland.neroarcade.exchange.ExchangeRules;

/**
 * Stage 6: the per-feature kill switches and the privacy retention keys. Each switch exists with the documented
 * default, is server-authoritative (a client cannot flip it), and is honoured by the code path it guards
 * ({@code economy.coinOp}: {@code CoinOpDisabledTest}; {@code pushNotifications.enabled}: {@code PushKillSwitchTest};
 * {@code leaderboards.enabled}: {@code LeaderboardKillSwitchTest}; {@code exchange.enabled} and
 * {@code multiplayer.enabled}: here). The retention ranges are pinned in the source, because Core clamps any value to
 * the range's maximum and that maximum is the hard cap PRIVACY.md promises.
 */
class KillSwitchesAndRetentionTest {

    private static ConfigValue<?> key(String name) {
        ConfigValue<?> value = NeroArcadeConfig.SCHEMA.value(name);
        assertNotNull(value, name + " is declared");
        return value;
    }

    @Test
    void killSwitchesExistWithTheirDefaultsAndAreServerAuthoritative() {
        Map<String, Boolean> defaults = Map.of(
                "exchange.enabled", true,
                "leaderboards.enabled", true,
                "multiplayer.enabled", true,
                "pushNotifications.enabled", true,
                "economy.coinOp", false);
        defaults.forEach((name, def) -> {
            ConfigValue<?> value = key(name);
            assertEquals(def, value.defaultValue(), name);
            assertTrue(value.serverAuthoritative(), name + " must be server-authoritative");
        });
    }

    @Test
    void retentionDefaultsAreThePromisedOnes() {
        assertEquals(90, key("privacy.leaderboardRetentionDays").defaultValue());
        assertEquals(365, key("privacy.personalBestRetentionDays").defaultValue());
        assertEquals(10, key("privacy.leaderboardMaxEntries").defaultValue());
        assertEquals(200, key("privacy.exchangeMaxListings").defaultValue());
        assertEquals(false, key("privacy.playHistory").defaultValue());
        assertEquals(60, key("privacy.exportCooldownSeconds").defaultValue());
        for (String name : new String[] {"privacy.leaderboardRetentionDays", "privacy.personalBestRetentionDays",
                "privacy.leaderboardMaxEntries", "privacy.exchangeMaxListings", "privacy.playHistory"}) {
            assertTrue(key(name).serverAuthoritative(), name);
        }
        assertFalse(NeroArcadeConfig.PLAY_HISTORY.get(), "no play history, ever");
    }

    /** Core clamps to the declared maximum, so the maximum is the hard cap: pin it in the source. */
    @Test
    void retentionHardCapsAre365Days() throws IOException {
        String src = Files.readString(TestFiles.repoRoot()
                .resolve("common/src/main/java/za/co/neroland/neroarcade/config/NeroArcadeConfig.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("\"privacy.leaderboardRetentionDays\", 90, 1, 365, true"), "row cap 365");
        assertTrue(src.contains("\"privacy.personalBestRetentionDays\", 365, 1, 365, true"), "best cap 365");
        assertTrue(src.contains("HARD CAP: 365 DAYS"), "the loud comment stays");
    }

    @Test
    void exchangeSwitchRefusesEveryAction() {
        UUID alice = UUID.randomUUID();
        assertEquals(Optional.of(ExchangeRules.DISABLED), ExchangeRules.publish(false, Optional.of(alice), alice, true));
        assertEquals(Optional.of(ExchangeRules.DISABLED), ExchangeRules.unpublish(false, alice, alice, true));
        assertEquals(Optional.of(ExchangeRules.DISABLED), ExchangeRules.heart(false, alice, UUID.randomUUID()));
    }

    @Test
    void multiplayerSwitchNeverWalksTheWorld() {
        AtomicInteger walks = new AtomicInteger();
        BlockPos hub = new BlockPos(3, 64, 7);
        BusTopology off = BusTopology.discoverIf(false, hub, () -> {
            walks.incrementAndGet();
            throw new AssertionError("the world must not be walked while multiplayer is off");
        });
        assertEquals(0, walks.get());
        assertTrue(off.cabinets().isEmpty(), "off: no cabinet joins, every cabinet plays solo");
        assertEquals(BusTopology.empty(hub), off);
        BusTopology on = BusTopology.discoverIf(true, hub, () -> {
            walks.incrementAndGet();
            return BusTopology.empty(hub);
        });
        assertEquals(1, walks.get(), "on: discovery runs");
        assertNotNull(on);
    }
}
