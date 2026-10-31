package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.compat.NbtCompat;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.PropertyValue.BoolValue;
import za.co.neroland.neroarcade.module.PropertyValue.EnumValue;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;
import za.co.neroland.neroarcade.module.pack.control.FourWayControl;

class SlotEntryCodecTest {

    private static final SlotEntry ENTRY = new SlotEntry(TestModules.id("pawn/cursor"),
            Map.of("speed", new IntValue(3), "fires", new BoolValue(true), "axis", new EnumValue("horizontal")));

    @Test
    void jsonShapeIsExact() {
        JsonElement json = SlotEntry.CODEC.encodeStart(JsonOps.INSTANCE, new SlotEntry(TestModules.id("pawn/cursor"),
                Map.of("speed", new IntValue(3)))).getOrThrow();
        assertEquals(JsonParser.parseString("{\"id\":\"neroarcade:pawn/cursor\",\"props\":{\"speed\":3}}"), json);
    }

    @Test
    void jsonRoundTrip() {
        JsonElement json = SlotEntry.CODEC.encodeStart(JsonOps.INSTANCE, ENTRY).getOrThrow();
        assertEquals(ENTRY, SlotEntry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void nbtRoundTripWritesBoolAsByteAndCoercesBack() throws ValidationError {
        Tag tag = SlotEntry.CODEC.encodeStart(NbtOps.INSTANCE, ENTRY).getOrThrow();
        CompoundTag props = NbtCompat.getCompound((CompoundTag) tag, "props").orElseThrow();
        assertTrue(props.get("fires") instanceof ByteTag);
        SlotEntry back = SlotEntry.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(new IntValue(1), back.props().get("fires"));
        assertEquals(new BoolValue(true), PropertySpec.bool("fires", false).coerce(back.props().get("fires")));
        assertEquals(ENTRY.props().get("speed"), back.props().get("speed"));
        assertEquals(ENTRY.props().get("axis"), back.props().get("axis"));
        PropertyValues coerced = PropertyValues.of(FourWayControl.INSTANCE,
                SlotEntry.CODEC.parse(NbtOps.INSTANCE, SlotEntry.CODEC.encodeStart(NbtOps.INSTANCE,
                        new SlotEntry(TestModules.id("control/four_way"), Map.of("diagonals", new BoolValue(true))))
                        .getOrThrow()).getOrThrow().props());
        assertTrue(coerced.getBool("diagonals"));
    }

    @Test
    void unsetPropsAreAbsent() {
        JsonElement json = SlotEntry.CODEC.encodeStart(JsonOps.INSTANCE, SlotEntry.of(TestModules.id("pawn/cursor")))
                .getOrThrow();
        assertFalse(json.getAsJsonObject().has("props"));
        Tag nbt = SlotEntry.CODEC.encodeStart(NbtOps.INSTANCE, SlotEntry.of(TestModules.id("pawn/cursor"))).getOrThrow();
        assertFalse(((CompoundTag) nbt).contains("props"));
    }

    @Test
    void fractionalNumbersAreRefused() {
        assertTrue(SlotEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"neroarcade:pawn/cursor\",\"props\":{\"speed\":1.5}}")).error().isPresent());
    }
}
