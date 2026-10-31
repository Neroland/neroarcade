package za.co.neroland.neroarcade;

import org.junit.jupiter.api.Assumptions;

/**
 * Guard for the few plain-JVM tests that touch chat filtering or item stacks. On the 1.21.x lines those vanilla
 * classes ({@code FilteredText} -> {@code HoverEvent} -> {@code ItemStack}) initialise the built-in registries, which
 * needs a game bootstrap that a plain JUnit run (no mod loader) cannot do, so the tests are skipped there; the
 * behaviour they check is version-independent and is covered by the 26.x runs.
 */
public final class TestBootstrap {

    private TestBootstrap() {
    }

    public static void ensure() {
        //? if <26.1 {
        /*Assumptions.assumeTrue(false, "needs a bootstrapped game on this Minecraft line");
        *///?}
    }
}
