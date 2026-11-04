package za.co.neroland.neroarcade.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.compat.PlayerMessages;
import za.co.neroland.neroarcade.diagnostics.DebugEncryption;
import za.co.neroland.neroarcade.diagnostics.DebugFaults;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.client.match.ClientMatches;

public final class DebugDumpCommand {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static long lastSuccess;

    private DebugDumpCommand() { }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("nsarcdebug")
                .executes(ctx -> say("Creates an encrypted technical report for Neroland. No chat, identities, coordinates or logs. Nothing is uploaded. Run /nsarcdebug dump to save it."))
                .then(LiteralArgumentBuilder.<S>literal("dump").executes(ctx -> dump())));
    }

    private static int dump() {
        if (BUSY.get() || (lastSuccess != 0 && System.nanoTime() - lastSuccess < 60_000_000_000L)) {
            say("Please wait: support dumps are limited to one per minute.");
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        final byte[] report;
        try {
            var match = ClientMatches.seated();
            String text = "NeroArcade support report v1\n"
                    + "version=" + Services.PLATFORM.getModVersion() + "\n"
                    + "loader=" + Services.PLATFORM.getPlatformName() + "\n"
                    + "java=" + Runtime.version() + "\n"
                    + "maxMemory=" + Runtime.getRuntime().maxMemory() + "\n"
                    + "screenRange=" + ArcadeClientConfig.screenRenderRange() + "\n"
                    + "seatedPhase=" + (match == null ? "none" : match.busPhase().name()) + "\n"
                    + "mods=" + Services.PLATFORM.getLoadedModIds().stream().limit(300)
                            .filter(value -> value.length() <= 160 && value.matches("[a-zA-Z0-9_. +:/()\\-]+"))
                            .toList() + "\n"
                    + za.co.neroland.neroarcade.client.render.ArcadeBerStats.report(
                            za.co.neroland.neroarcade.client.render.CabinetScreens.live(),
                            za.co.neroland.neroarcade.client.render.TextureLru.DEFAULT_CAP,
                            za.co.neroland.neroarcade.client.render.AttractReplays.live())
                    + "\nrecentHandledFaults=\n" + DebugFaults.snapshot() + "\n";
            report = text.getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] support snapshot failed ({})", e.getClass().getSimpleName());
            say("Could not collect support diagnostics. No report was saved.");
            return 0;
        }
        Path directory = mc.gameDirectory.toPath().resolve("neroarcade-debug");
        BUSY.set(true);
        say("Creating an encrypted support report; nothing will be uploaded...");
        CompletableFuture.runAsync(() -> {
            try (var input = DebugDumpCommand.class.getResourceAsStream("/neroarcade-support-public.der")) {
                if (input == null) throw new IOException("Missing support public key");
                var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(input.readAllBytes()));
                byte[] encrypted = DebugEncryption.encrypt(report, key);
                Files.createDirectories(directory);
                try (var files = Files.list(directory)) {
                    if (files.filter(path -> path.getFileName().toString().matches("support-[0-9a-f\\-]+\\.nad")).count() >= 10) {
                        mc.execute(() -> {
                            BUSY.set(false);
                            say("Ten support dumps already exist. Delete old files from neroarcade-debug before creating another.");
                        });
                        return;
                    }
                }
                Path output = directory.resolve("support-" + java.util.UUID.randomUUID() + ".nad");
                Files.write(output, encrypted, StandardOpenOption.CREATE_NEW);
                mc.execute(() -> {
                    lastSuccess = System.nanoTime();
                    BUSY.set(false);
                    say("Saved neroarcade-debug/" + output.getFileName() + ". Send this encrypted file to Neroland support. Delete it when no longer needed.");
                });
            } catch (Exception e) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] support dump failed ({})", e.getClass().getSimpleName());
                mc.execute(() -> {
                    BUSY.set(false);
                    say("Could not save the encrypted report. Check disk space and folder permissions.");
                });
            }
        });
        return 1;
    }

    private static int say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) PlayerMessages.chat(player, Component.literal(text));
        return 1;
    }
}
