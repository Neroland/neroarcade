package za.co.neroland.neroarcade.telemetry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A cheap textual guard: no INFO, WARN or ERROR log statement in the main sources passes an argument that looks
 * like a player id, a name, a player, an author or credit, a share or an amount; and no telemetry call (handled
 * exception tags, breadcrumbs) is handed one either. The real review is done by reading the code; this catches the
 * obvious regression. Stage 6 widened it (error level, player/author/credit/profile words, telemetry calls).
 */
class NoPersonalDataInLogsTest {

    private static final Pattern CALL = Pattern.compile(
            "LOGGER\\.(info|warn|error)\\(|NeroArcadeTelemetry\\.(captureHandledException|breadcrumb)\\(");
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(UUID|uuid|getUUID|getName|share|amount|player|"
            + "players|getPlayer|getGameProfile|profile|author|authorUuid|authorCredit|credit|getDisplayName|subject)\\b");

    @Test
    void infoAndWarnLinesCarryNoPersonalData() throws IOException {
        Path root = findSources();
        assertNotNull(root, "common/src/main/java not found above " + Path.of("").toAbsolutePath());
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = CALL.matcher(text);
                while (m.find()) {
                    int end = text.indexOf(';', m.end());
                    String statement = text.substring(m.end(), end < 0 ? text.length() : end);
                    String arguments = withoutStringLiterals(statement);
                    checked++;
                    if (FORBIDDEN.matcher(arguments).find()) {
                        offenders.add(root.relativize(file) + ": " + statement.strip());
                    }
                }
            }
        }
        assertTrue(checked > 0, "the scan found the log statements");
        assertTrue(offenders.isEmpty(), "log lines with personal-data-looking arguments: " + offenders);
    }

    private static String withoutStringLiterals(String statement) {
        // An exception's class name is fine (it is how failures are logged); a player's name is not.
        return statement.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"")
                .replaceAll("getClass\\(\\)\\.get(Simple)?Name\\(\\)", "");
    }

    private static Path findSources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("common").resolve("src").resolve("main").resolve("java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
