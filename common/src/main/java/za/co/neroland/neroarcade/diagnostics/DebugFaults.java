package za.co.neroland.neroarcade.diagnostics;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DebugFaults {
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private DebugFaults() { }

    public static synchronized void capture(Throwable error) {
        StringBuilder value = new StringBuilder(error.getClass().getName());
        int frames = 0;
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("za.co.neroland.neroarcade.") && frames++ < 8) {
                value.append("\n  ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber());
            }
        }
        if (RECENT.size() == 16) RECENT.removeFirst();
        RECENT.addLast(value.toString());
    }

    public static synchronized String snapshot() {
        return String.join("\n", RECENT);
    }
}
