package za.co.neroland.neroarcade.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.client.render.ArcadeBerStats;
import za.co.neroland.neroarcade.client.render.AttractReplays;
import za.co.neroland.neroarcade.client.render.CabinetScreens;
import za.co.neroland.neroarcade.client.render.TextureLru;
import za.co.neroland.neroarcade.command.GalleryLayout;

/**
 * CLIENT, creative-debug: the client commands that sit beside the gallery. Their roots are {@code /nsarcgallery} and
 * {@code /nsarcber}, never {@code /neroarcade}: a client command root that shares its name with a server command
 * shadows the server subtree.
 *
 * <ul>
 *   <li>{@code /nsarcgallery capture [time]}: freezes time and weather ({@code advance_time} and
 *       {@code advance_weather} off, {@code time set}, clear weather), rebuilds the gallery where you stand
 *       ({@code /neroarcade gallery}), kills dropped items within 64 blocks, then poses the camera for each of the
 *       {@link GalleryLayout#SHOTS} with the HUD hidden and writes {@code screenshots/neroarcade/<shot>.png} — the
 *       folder the Modrinth gallery workflow mirrors. During the hero frame it also writes the render-budget record
 *       to {@code screenshots/neroarcade/budget.txt}. {@code time} is {@code day}, {@code noon}, {@code night} or
 *       {@code midnight} (default {@code night}).</li>
 *   <li>{@code /nsarcber} prints NeroArcade's renderer numbers averaged over the last 200 frames;
 *       {@code /nsarcber reset} starts a fresh window.</li>
 * </ul>
 *
 * Wired from each loader's client-command hook and client tick. The run warns when anyone else is in the world
 * (their name and skin could appear in a frame); nothing is recorded about anyone, and the files stay in this
 * client's own folder. It changes game rules and replaces blocks: use a test world.
 */
public final class GalleryCapture {

    private static final String FOLDER = "neroarcade";
    /** Ticks for chunks to load after the teleport, before the build. */
    private static final int WARMUP_TICKS = 60;
    /** Ticks after the build for every cabinet to pick up its cartridge and start its demo. */
    private static final int SCENE_TICKS = 100;
    /** Ticks a pose holds before its frame is taken. */
    private static final int SETTLE_TICKS = 15;
    /** The budget frame's window: the hero pose holds this long with the counters reset first. */
    private static final int BUDGET_TICKS = 220;

    private enum Phase { WARMUP, SCENE, POSE, DONE }

    private static final Deque<GalleryLayout.Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean flyingWas;
    private static Phase phase = Phase.DONE;
    private static int wait;
    private static @Nullable BlockPos origin;
    private static GalleryLayout.@Nullable Shot current;

    private GalleryCapture() {
    }

    /** Registers {@code /nsarcgallery} and {@code /nsarcber}; generic so one call serves every loader. */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("nsarcgallery")
                .then(LiteralArgumentBuilder.<S>literal("capture")
                        .executes(ctx -> start("night"))
                        .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                .executes(ctx -> start(StringArgumentType.getString(ctx, "time"))))));
        dispatcher.register(LiteralArgumentBuilder.<S>literal("nsarcber")
                .executes(ctx -> berRecord())
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
                    ArcadeBerStats.reset();
                    say("NeroArcade renderer counters reset.");
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private static int berRecord() {
        for (String line : record().split("\\R")) {
            say(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    static String record() {
        return ArcadeBerStats.report(CabinetScreens.live(), TextureLru.DEFAULT_CAP, AttractReplays.live());
    }

    private static int start(String time) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            say("A NeroArcade gallery capture is already running.");
            return 0;
        }
        origin = player.blockPosition();
        QUEUE.clear();
        QUEUE.addAll(GalleryLayout.SHOTS);
        new File(mc.gameDirectory, "screenshots/" + FOLDER).mkdirs();
        hudWasHidden = isHudHidden(mc);
        flyingWas = player.getAbilities().flying;
        setFlying(player, true);
        BlockPos o = origin;
        for (String cmd : List.of(
                "tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5),
                "gamerule advance_time false",
                "gamerule advance_weather false",
                "time set " + time,
                "weather clear")) {
            player.connection.sendCommand(cmd);
        }
        if (mc.level.players().size() > 1) {
            say("Other players are in this world: their names and skins may appear in these screenshots. Check "
                    + "every capture before you publish it.");
        }
        say("NeroArcade gallery capture: building the gallery here, time=" + time + ", " + QUEUE.size()
                + " shots -> screenshots/" + FOLDER + "/ (HUD hidden).");
        running = true;
        phase = Phase.WARMUP;
        wait = WARMUP_TICKS;
        return Command.SINGLE_SUCCESS;
    }

    /** One client tick; a no-op unless a capture runs. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos o = origin;
        if (player == null || o == null) {
            finish(mc);
            return;
        }
        switch (phase) {
            case WARMUP -> {
                // stand on the origin: the server builds the gallery where the player stands
                player.snapTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 180f, 0f);
                player.setDeltaMovement(Vec3.ZERO);
                if (--wait <= 0) {
                    player.connection.sendCommand("neroarcade gallery");
                    phase = Phase.SCENE;
                    wait = SCENE_TICKS;
                }
            }
            case SCENE -> {
                hold(player, o, GalleryLayout.SHOTS.get(0));
                if (--wait <= 0) {
                    player.connection.sendCommand("kill @e[type=item,distance=..64]");
                    setHudHidden(mc, true);
                    next(player, o);
                }
            }
            case POSE -> {
                GalleryLayout.Shot shot = current;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                hold(player, o, shot);
                if (--wait <= 0) {
                    grab(mc, shot.name());
                    if (GalleryLayout.BUDGET_SHOT.equals(shot.name())) {
                        writeBudget(mc);
                    }
                    next(player, o);
                }
            }
            case DONE -> finish(mc);
        }
    }

    private static void next(LocalPlayer player, BlockPos o) {
        GalleryLayout.Shot shot = QUEUE.poll();
        current = shot;
        if (shot == null) {
            phase = Phase.DONE;
            return;
        }
        hold(player, o, shot);
        boolean budget = GalleryLayout.BUDGET_SHOT.equals(shot.name());
        if (budget) {
            ArcadeBerStats.reset();
        }
        wait = budget ? BUDGET_TICKS : SETTLE_TICKS;
        phase = Phase.POSE;
    }

    /** Pins the camera (the player) to a pose relative to the origin block's centre. */
    private static void hold(LocalPlayer player, BlockPos o, GalleryLayout.Shot shot) {
        Vec3 cam = new Vec3(o.getX() + 0.5 + shot.x(), o.getY() + shot.y(), o.getZ() + 0.5 + shot.z());
        Vec3 tgt = new Vec3(o.getX() + 0.5 + shot.tx(), o.getY() + shot.ty(), o.getZ() + 0.5 + shot.tz());
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        player.snapTo(cam.x, cam.y - player.getEyeHeight(), cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void writeBudget(Minecraft mc) {
        String text = record() + System.lineSeparator() + "within budget: " + ArcadeBerStats.withinBudget();
        for (String line : text.split("\\R")) {
            say(line);
        }
        File out = new File(mc.gameDirectory, "screenshots/" + FOLDER + "/budget.txt");
        try {
            Files.writeString(out.toPath(), text + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            say("Could not write screenshots/" + FOLDER + "/budget.txt (" + e.getClass().getSimpleName() + ").");
        }
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, FOLDER + "/" + name + ".png", mainRenderTarget(mc), 1, msg -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(msg);
            }
        });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (mc.player != null) {
            setFlying(mc.player, flyingWas);
        }
        running = false;
        current = null;
        QUEUE.clear();
        phase = Phase.DONE;
        say("NeroArcade gallery capture done: see screenshots/" + FOLDER + "/. /neroarcade gallery clear removes the "
                + "hall.");
    }

    private static void setFlying(LocalPlayer player, boolean fly) {
        player.getAbilities().flying = fly && player.getAbilities().mayfly;
        player.onUpdateAbilities();
    }

    private static void say(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(text));
        }
    }

    // --- the HUD flag and the main framebuffer moved in 26.2 ------------------------------------------------

    private static boolean isHudHidden(Minecraft mc) {
        //? if <26.2 {
        /*return mc.options.hideGui;
        *///?} else {
        return mc.gui.hud.isHidden();
        //?}
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        //? if <26.2 {
        /*mc.options.hideGui = hidden;
        *///?} else {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
        //?}
    }

    private static RenderTarget mainRenderTarget(Minecraft mc) {
        //? if <26.2 {
        /*return mc.getMainRenderTarget();
        *///?} else {
        return mc.gameRenderer.mainRenderTarget();
        //?}
    }
}
