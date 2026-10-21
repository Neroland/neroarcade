package za.co.neroland.neroarcade.cabinet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.runtime.Budgets;

/** The live {@link HostEnv}: a server level, the host block and its power check. */
public final class LevelHostEnv implements HostEnv {

    private final ServerLevel level;
    private final Vec3 centre;
    private final BooleanSupplier powered;

    public LevelHostEnv(ServerLevel level, BlockPos pos, BooleanSupplier powered) {
        this.level = level;
        this.centre = Vec3.atCenterOf(pos);
        this.powered = powered;
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
    }

    @Override
    public long newSeed() {
        return level.getRandom().nextLong();
    }

    @Override
    public Collection<UUID> viewers() {
        return viewers(level, centre);
    }

    /** UUIDs of the players in {@code level} within {@code screenRenderRange} of {@code centre}. */
    public static List<UUID> viewers(ServerLevel level, Vec3 centre) {
        double range = Budgets.screenRenderRange();
        double rangeSq = range * range;
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(centre) <= rangeSq) {
                out.add(player.getUUID());
            }
        }
        return out;
    }

    @Override
    public void send(UUID player, CustomPacketPayload payload) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        if (target != null) {
            Services.NETWORK.sendToPlayer(target, payload);
        }
    }

    @Override
    public boolean powered() {
        return powered.getAsBoolean();
    }
}
