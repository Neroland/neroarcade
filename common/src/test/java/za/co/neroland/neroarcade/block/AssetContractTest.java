package za.co.neroland.neroarcade.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.level.block.state.properties.Property;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.registry.ArcadeIds;

/**
 * The asset ↔ code contract (004 "Registry ids"): every property name and value a blockstate JSON uses is one the
 * block declares; every declared property the JSON does not use is BER-only by decision ({@code editing},
 * {@code scale}); every block and item has its asset files; the sound ids match {@code sounds.json}.
 */
class AssetContractTest {

    private static final Path ASSETS = TestFiles.repoRoot().resolve("common/src/main/resources/assets/neroarcade");
    /** Declared but intentionally absent from the JSON: the BER reads them. */
    private static final Set<String> BER_ONLY = Set.of("editing", "scale");

    @Test
    void blockstateJsonUsesOnlyDeclaredPropertiesAndValues() {
        assertEquals(new TreeSet<>(ArcadeIds.BLOCKS), new TreeSet<>(ArcadeBlockProperties.BY_BLOCK.keySet()));
        for (String block : ArcadeIds.BLOCKS) {
            Path file = ASSETS.resolve("blockstates/" + block + ".json");
            assertTrue(Files.isRegularFile(file), "missing " + file);
            Map<String, Set<String>> used = usedProperties(JsonParser.parseString(TestFiles.read(file)).getAsJsonObject());
            Map<String, Property<?>> declared = new HashMap<>();
            for (Property<?> property : ArcadeBlockProperties.BY_BLOCK.get(block)) {
                declared.put(property.getName(), property);
            }
            for (Map.Entry<String, Set<String>> entry : used.entrySet()) {
                Property<?> property = declared.get(entry.getKey());
                assertTrue(property != null, block + ": JSON uses undeclared property " + entry.getKey());
                for (String value : entry.getValue()) {
                    assertTrue(property.getValue(value).isPresent(),
                            block + ": " + entry.getKey() + "=" + value + " is not a value of the declared property");
                }
            }
            for (String name : declared.keySet()) {
                assertTrue(used.containsKey(name) || BER_ONLY.contains(name),
                        block + ": declares " + name + " but no blockstate JSON reads it");
            }
        }
    }

    private static Map<String, Set<String>> usedProperties(JsonObject root) {
        Map<String, Set<String>> out = new HashMap<>();
        if (root.has("variants")) {
            for (String key : root.getAsJsonObject("variants").keySet()) {
                for (String pair : key.split(",")) {
                    if (pair.contains("=")) {
                        String[] kv = pair.split("=", 2);
                        out.computeIfAbsent(kv[0], k -> new TreeSet<>()).add(kv[1]);
                    }
                }
            }
        } else {
            for (JsonElement part : root.getAsJsonArray("multipart")) {
                JsonObject object = part.getAsJsonObject();
                if (object.has("when")) {
                    collectWhen(object.getAsJsonObject("when"), out);
                }
            }
        }
        return out;
    }

    private static void collectWhen(JsonObject when, Map<String, Set<String>> out) {
        for (Map.Entry<String, JsonElement> entry : when.entrySet()) {
            if (entry.getKey().equals("OR") || entry.getKey().equals("AND")) {
                entry.getValue().getAsJsonArray().forEach(child -> collectWhen(child.getAsJsonObject(), out));
            } else {
                for (String value : entry.getValue().getAsString().split("\\|")) {
                    out.computeIfAbsent(entry.getKey(), k -> new TreeSet<>()).add(value);
                }
            }
        }
    }

    @Test
    void everyItemHasItsModelDefinition() {
        for (String item : ArcadeIds.ITEMS) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("items/" + item + ".json")), "missing items/" + item + ".json");
        }
    }

    @Test
    void soundIdsMatchSoundsJson() {
        JsonObject sounds = JsonParser.parseString(TestFiles.read(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        assertEquals(new TreeSet<>(sounds.keySet()), new TreeSet<>(ArcadeIds.SOUNDS));
        Map<String, String> lang = TestFiles.lang();
        for (String key : ArcadeIds.SOUNDS) {
            String subtitle = sounds.getAsJsonObject(key).get("subtitle").getAsString();
            assertTrue(lang.containsKey(subtitle), "no lang for " + subtitle);
        }
    }

    @Test
    void everyBlockAndItemIsNamedAndTheCabinetStatusWordsExist() {
        Map<String, String> lang = TestFiles.lang();
        for (String block : ArcadeIds.BLOCKS) {
            assertTrue(lang.containsKey("block.neroarcade." + block), block);
            assertTrue(lang.containsKey("block.neroarcade." + block + ".tooltip"), block + " tooltip");
        }
        for (String item : List.of(ArcadeIds.MODULE_CHIP, ArcadeIds.CARTRIDGE, ArcadeIds.BLANK_CARTRIDGE, ArcadeIds.BLANK_CHIP)) {
            assertTrue(lang.containsKey("item.neroarcade." + item), item);
        }
        for (CabinetStatus status : CabinetStatus.values()) {
            assertTrue(lang.containsKey(status.langKey()), status.langKey());
        }
        for (za.co.neroland.neroarcade.cabinet.DropReason reason : za.co.neroland.neroarcade.cabinet.DropReason.values()) {
            assertTrue(lang.containsKey(reason.langKey()), reason.langKey());
        }
        for (za.co.neroland.neroarcade.cabinet.BusPhase phase : za.co.neroland.neroarcade.cabinet.BusPhase.values()) {
            assertTrue(lang.containsKey(phase.langKey()), phase.langKey());
        }
    }
}
