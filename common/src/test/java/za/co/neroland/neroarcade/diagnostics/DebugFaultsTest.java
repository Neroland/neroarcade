package za.co.neroland.neroarcade.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DebugFaultsTest {
    @Test
    void retainsOnlyBoundedCodeMetadataNeverMessagesPathsOrForeignFrames() {
        var error = new IllegalStateException("private player chat and token");
        error.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("za.co.neroland.neroarcade.runtime.ArcadeRuntime", "step", "C:/Users/secret/source.java", 42),
                new StackTraceElement("foreign.UserContent", "sensitive", "private", 7)
        });
        for (int i = 0; i < 25; i++) DebugFaults.capture(error);
        String report = DebugFaults.snapshot();
        assertTrue(report.contains("ArcadeRuntime.step:42"));
        assertFalse(report.contains("private"));
        assertFalse(report.contains("secret"));
        assertFalse(report.contains("foreign"));
        assertEquals(16, report.lines().filter(line -> line.equals("java.lang.IllegalStateException")).count());
    }
}
