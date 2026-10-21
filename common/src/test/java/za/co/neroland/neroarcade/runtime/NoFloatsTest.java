package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Integer-only, by reflection: no float/double field, parameter or return type anywhere in {@code game},
 * {@code runtime} or {@code module} (sub-packages included), and no {@code HashMap}/{@code HashSet} field in
 * {@code runtime}.
 */
class NoFloatsTest {

    private static List<Class<?>> classesUnder(String pkg) throws IOException, URISyntaxException, ClassNotFoundException {
        List<Class<?>> out = new ArrayList<>();
        String path = pkg.replace('.', '/');
        for (URL url : Collections.list(NoFloatsTest.class.getClassLoader().getResources(path))) {
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Path root = Path.of(url.toURI());
            if (root.toString().replace('\\', '/').contains("/test/")) {
                continue; // main classes only
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    String name = pkg + "." + rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                    out.add(Class.forName(name, false, NoFloatsTest.class.getClassLoader()));
                }
            }
        }
        return out;
    }

    private static boolean isFloat(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type == float.class || type == double.class || type == Float.class || type == Double.class;
    }

    @Test
    void noFloatsInTheSimulation() throws Exception {
        int checked = 0;
        for (String pkg : List.of("za.co.neroland.neroarcade.game", "za.co.neroland.neroarcade.runtime",
                "za.co.neroland.neroarcade.module")) {
            List<Class<?>> classes = classesUnder(pkg);
            assertFalse(classes.isEmpty(), "no classes found under " + pkg);
            for (Class<?> c : classes) {
                checked++;
                for (Field f : c.getDeclaredFields()) {
                    assertFalse(isFloat(f.getType()), c.getName() + "." + f.getName());
                }
                for (Method m : c.getDeclaredMethods()) {
                    assertFalse(isFloat(m.getReturnType()), c.getName() + "." + m.getName() + " returns a float");
                    for (Class<?> p : m.getParameterTypes()) {
                        assertFalse(isFloat(p), c.getName() + "." + m.getName() + " takes a float");
                    }
                }
                for (Constructor<?> k : c.getDeclaredConstructors()) {
                    for (Class<?> p : k.getParameterTypes()) {
                        assertFalse(isFloat(p), c.getName() + " constructor takes a float");
                    }
                }
            }
        }
        assertTrue(checked > 40, "expected the whole simulation to be scanned, saw " + checked + " classes");
    }

    @Test
    void noHashCollectionsInTheRuntime() throws Exception {
        for (Class<?> c : classesUnder("za.co.neroland.neroarcade.runtime")) {
            for (Field f : c.getDeclaredFields()) {
                Class<?> t = f.getType();
                assertFalse(java.util.HashMap.class.isAssignableFrom(t) || java.util.HashSet.class.isAssignableFrom(t),
                        c.getName() + "." + f.getName() + " is a hash collection");
            }
        }
    }
}
