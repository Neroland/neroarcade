package za.co.neroland.neroarcade.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** POPIA/GDPR: the OS account never survives {@link NeroArcadeTelemetry#scrub}. Plain JVM. */
class TelemetryScrubTest {

    @Test
    void removesThisMachinesHomeDirectory() {
        String home = System.getProperty("user.home");
        String scrubbed = NeroArcadeTelemetry.scrub("failed to read " + home + "/saves/world/level.dat");
        assertFalse(scrubbed.contains(home), scrubbed);
    }

    @Test
    void masksForeignHomePaths() {
        assertEquals("open /~\\mods\\x.jar", NeroArcadeTelemetry.scrub("open C:\\Users\\someone\\mods\\x.jar"));
        assertEquals("/~/game", NeroArcadeTelemetry.scrub("/home/someone/game"));
    }

    @Test
    void masksUuids() {
        String id = java.util.UUID.randomUUID().toString();
        String scrubbed = NeroArcadeTelemetry.scrub("no account " + id + " (" + id.toUpperCase() + ")");
        assertEquals("no account <uuid> (<uuid>)", scrubbed);
    }

    @Test
    void nullStaysNull() {
        assertNull(NeroArcadeTelemetry.scrub(null));
    }
}
