package za.co.neroland.neroarcade.cabinet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A {@link HostEnv} for plain-JVM tests: a manual clock, a viewer set, a power switch and a packet log. */
final class FakeHostEnv implements HostEnv {

    record Sent(UUID to, CustomPacketPayload payload) {
    }

    long time;
    long seed = 0x4E45524FL;
    boolean powered = true;
    final Set<UUID> viewers = new LinkedHashSet<>();
    final List<Sent> sent = new ArrayList<>();

    @Override
    public long gameTime() {
        return time;
    }

    @Override
    public long newSeed() {
        return seed;
    }

    @Override
    public Collection<UUID> viewers() {
        return List.copyOf(viewers);
    }

    @Override
    public void send(UUID player, CustomPacketPayload payload) {
        sent.add(new Sent(player, payload));
    }

    @Override
    public boolean powered() {
        return powered;
    }

    <T extends CustomPacketPayload> List<T> sentTo(UUID player, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Sent s : sent) {
            if (s.to().equals(player) && type.isInstance(s.payload())) {
                out.add(type.cast(s.payload()));
            }
        }
        return out;
    }
}
