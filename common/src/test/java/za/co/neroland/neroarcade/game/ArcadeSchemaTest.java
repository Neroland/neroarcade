package za.co.neroland.neroarcade.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

class ArcadeSchemaTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    @Test
    void currentSchemaDecodes() {
        JsonObject root = GameDefCodecTest.load("orb_dodge.json");
        var result = ArcadeSchema.migrate(1, new Dynamic<>(JsonOps.INSTANCE, root));
        assertEquals("Orb Dodge", result.getOrThrow().title());
    }

    @Test
    void newerSchemaFailsClosed() {
        JsonObject root = GameDefCodecTest.load("orb_dodge.json");
        assertTrue(ArcadeSchema.migrate(ArcadeSchema.CURRENT + 1, new Dynamic<>(JsonOps.INSTANCE, root)).error().isPresent());
        assertTrue(ArcadeSchema.migrate(0, new Dynamic<>(JsonOps.INSTANCE, root)).error().isPresent());
    }

    @Test
    void schemaIsReadBeforeTheRecordCodec() {
        JsonObject garbage = new JsonObject();
        garbage.addProperty("schema", 7);
        garbage.addProperty("title", 12);
        assertEquals(7, ArcadeSchema.readSchema(new Dynamic<>(JsonOps.INSTANCE, garbage)).getOrThrow());
        GameDefCodecTest.assertCode(ValidationError.Code.NEWER_SCHEMA, garbage.toString());
        JsonObject fractional = new JsonObject();
        fractional.addProperty("schema", 1.5);
        assertTrue(ArcadeSchema.readSchema(new Dynamic<>(JsonOps.INSTANCE, fractional)).error().isPresent());
    }
}
