package za.co.neroland.neroarcade.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every registered module class (shipped and test chips) holds no world/server references, no
 * {@code java.util.Random}, no floats and no mutable statics — so its hooks cannot reach outside the match.
 */
class HookPurityTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    @Test
    void moduleClassesCannotReachOutsideTheMatch() {
        assertFalse(ModuleRegistry.all().isEmpty());
        for (ArcadeModule module : ModuleRegistry.all()) {
            for (Class<?> c = module.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    String where = c.getName() + "." + field.getName();
                    Class<?> type = field.getType();
                    while (type.isArray()) {
                        type = type.getComponentType();
                    }
                    assertFalse(type == float.class || type == double.class || type == Float.class
                            || type == Double.class, where + " is a float");
                    assertFalse(type == java.util.Random.class, where + " is a java.util.Random");
                    String name = type.getName();
                    assertFalse(name.startsWith("net.minecraft.world.") || name.startsWith("net.minecraft.server."),
                            where + " references " + name);
                    if (Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                        assertTrue(Modifier.isFinal(field.getModifiers()), where + " is a mutable static");
                    }
                }
            }
        }
    }

    /**
     * A registered module is a singleton shared by every match, so a mutable instance field is a static in
     * disguise and would desync. Every instance field must be final and of a primitive, {@code String},
     * {@code Identifier}, {@code PropertySpec} or immutable {@code List}/{@code Set} type — never an array.
     */
    @Test
    void moduleInstanceFieldsAreFinalAndImmutable() throws IllegalAccessException {
        for (ArcadeModule module : ModuleRegistry.all()) {
            for (Class<?> c = module.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    String where = c.getName() + "." + field.getName();
                    Class<?> type = field.getType();
                    assertTrue(Modifier.isFinal(field.getModifiers()), where + " is a mutable instance field");
                    assertFalse(type.isArray(), where + " is an array");
                    boolean collection = type == List.class || type == Set.class;
                    assertTrue(type.isPrimitive() || type == String.class || type == Identifier.class
                            || type == PropertySpec.class || collection, where + " has a disallowed type " + type.getName());
                    if (collection) {
                        field.setAccessible(true);
                        Object value = field.get(module);
                        String impl = value == null ? "null" : value.getClass().getName();
                        assertTrue(value == null || impl.startsWith("java.util.ImmutableCollections")
                                || impl.startsWith("java.util.Collections$Unmodifiable")
                                || impl.startsWith("java.util.Collections$Empty")
                                || impl.startsWith("java.util.Collections$Singleton"),
                                where + " holds a mutable " + impl);
                    }
                }
            }
        }
    }
}
