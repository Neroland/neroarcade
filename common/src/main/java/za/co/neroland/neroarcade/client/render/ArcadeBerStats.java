package za.co.neroland.neroarcade.client.render;

import java.util.Locale;

/**
 * Per-frame counters for NeroArcade's block-entity renderers, read by the client command {@code /nsarcber} and
 * written into the render-budget record (the family BER gates: at most 60 000 vertices and 1.5 ms of BER time a
 * frame, within 10 % of the no-animation baseline, no BufferBuilder warnings).
 *
 * <p>Renderer contract (a few integer adds; render thread only):
 * <ol>
 *   <li>first line of {@code extractRenderState}: {@code frame(gameTime, partialTick)} — a new pair closes the
 *       previous frame;</li>
 *   <li>{@code long t = start();} … {@code stop(t)} around extract and around submit;</li>
 *   <li>{@code culled()} from a {@code shouldRender} that returns false and on an extract that submits nothing;</li>
 *   <li>{@code submitted()} once per renderer that submits geometry and {@code vertices(n)} with what it emits;
 *       {@code upload()} for every screen-texture upload.</li>
 * </ol>
 * The last {@value #WINDOW} closed frames are averaged. Pure Java, unit-testable.
 */
public final class ArcadeBerStats {

    /** Frames averaged by {@link #average()}. */
    public static final int WINDOW = 200;
    /** The family gates the record is compared with. */
    public static final int VERTEX_BUDGET = 60_000;
    public static final double MS_BUDGET = 1.5;

    private static final long[] SUBMITTED = new long[WINDOW];
    private static final long[] CULLED = new long[WINDOW];
    private static final long[] VERTICES = new long[WINDOW];
    private static final long[] NANOS = new long[WINDOW];
    private static final long[] UPLOADS = new long[WINDOW];
    private static int head;
    private static int filled;

    private static long curSubmitted;
    private static long curCulled;
    private static long curVertices;
    private static long curNanos;
    private static long curUploads;
    private static long frameGameTime = Long.MIN_VALUE;
    private static float framePartial = Float.NaN;
    private static boolean frameOpen;

    /** An average over the window. */
    public record Snapshot(int frames, double submitted, double culled, double vertices, double millis,
            double uploads, long peakVertices, double peakMillis) {
    }

    private ArcadeBerStats() {
    }

    public static void frame(long gameTime, float partialTick) {
        if (frameOpen && gameTime == frameGameTime && Float.compare(partialTick, framePartial) == 0) {
            return;
        }
        if (frameOpen) {
            endFrame();
        }
        frameGameTime = gameTime;
        framePartial = partialTick;
        frameOpen = true;
    }

    public static void endFrame() {
        if (!frameOpen) {
            return;
        }
        SUBMITTED[head] = curSubmitted;
        CULLED[head] = curCulled;
        VERTICES[head] = curVertices;
        NANOS[head] = curNanos;
        UPLOADS[head] = curUploads;
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) {
            filled++;
        }
        curSubmitted = 0;
        curCulled = 0;
        curVertices = 0;
        curNanos = 0;
        curUploads = 0;
        frameOpen = false;
    }

    public static void submitted() {
        curSubmitted++;
    }

    public static void culled() {
        curCulled++;
    }

    public static void vertices(int count) {
        if (count > 0) {
            curVertices += count;
        }
    }

    public static void upload() {
        curUploads++;
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void stop(long startNanos) {
        long d = System.nanoTime() - startNanos;
        if (d > 0) {
            curNanos += d;
        }
    }

    /** Adds explicit nanoseconds (tests). */
    public static void nanos(long nanos) {
        if (nanos > 0) {
            curNanos += nanos;
        }
    }

    public static Snapshot average() {
        int n = filled;
        if (n == 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);
        }
        long s = 0;
        long c = 0;
        long v = 0;
        long t = 0;
        long u = 0;
        long peakV = 0;
        long peakT = 0;
        for (int i = 0; i < n; i++) {
            s += SUBMITTED[i];
            c += CULLED[i];
            v += VERTICES[i];
            t += NANOS[i];
            u += UPLOADS[i];
            peakV = Math.max(peakV, VERTICES[i]);
            peakT = Math.max(peakT, NANOS[i]);
        }
        return new Snapshot(n, (double) s / n, (double) c / n, (double) v / n, t / 1.0e6 / n, (double) u / n, peakV,
                peakT / 1.0e6);
    }

    /** True when the window's peaks are inside the family gates. */
    public static boolean withinBudget() {
        Snapshot a = average();
        return a.peakVertices() <= VERTEX_BUDGET && a.millis() <= MS_BUDGET;
    }

    /** The multi-line record ({@code /nsarcber}); {@code screens} and {@code demos} are the live pool sizes. */
    public static String report(int screens, int screenCap, int demos) {
        Snapshot a = average();
        return String.format(Locale.ROOT,
                "NeroArcade BER stats over %d frames%n"
                        + "  renderers submitted/frame: %.1f%n"
                        + "  renderers culled/frame:    %.1f%n"
                        + "  vertices/frame:            %.0f (peak %d; budget %d)%n"
                        + "  BER ms/frame:              %.3f (peak %.3f; budget %.1f)%n"
                        + "  screen uploads/frame:      %.2f%n"
                        + "  live screens:              %d of %d; attract replays running: %d",
                a.frames(), a.submitted(), a.culled(), a.vertices(), a.peakVertices(), VERTEX_BUDGET, a.millis(),
                a.peakMillis(), MS_BUDGET, a.uploads(), screens, screenCap, demos);
    }

    public static void reset() {
        for (int i = 0; i < WINDOW; i++) {
            SUBMITTED[i] = 0;
            CULLED[i] = 0;
            VERTICES[i] = 0;
            NANOS[i] = 0;
            UPLOADS[i] = 0;
        }
        head = 0;
        filled = 0;
        curSubmitted = 0;
        curCulled = 0;
        curVertices = 0;
        curNanos = 0;
        curUploads = 0;
        frameGameTime = Long.MIN_VALUE;
        framePartial = Float.NaN;
        frameOpen = false;
    }
}
