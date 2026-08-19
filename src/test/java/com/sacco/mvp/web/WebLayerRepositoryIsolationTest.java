package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebLayerRepositoryIsolationTest {
    private static final Path WEB_SOURCE = Path.of("src/main/java/com/sacco/mvp/web");
    private static final Pattern REPOSITORY_IMPORT = Pattern.compile(
        "^\\s*import\\s+com\\.sacco\\.mvp\\.repository\\.[A-Za-z0-9_.*]+\\s*;",
        Pattern.MULTILINE
    );
    private static final Pattern REPOSITORY_FIELD = Pattern.compile(
        "\\b(?:private|protected|public)\\s+(?:final\\s+)?(?:[A-Za-z0-9_.]+\\.)*([A-Z][A-Za-z0-9_]*Repository)\\b"
    );
    private static final Pattern SPRING_SECURITY_SESSION_REPOSITORY = Pattern.compile(
        "HttpSessionSecurityContextRepository"
    );

    @Test
    void webPackageClassesMustNotDeclareSpringDataRepositoryDependencies() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(WEB_SOURCE)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .forEach(path -> collectViolations(path, violations));
        }
        assertTrue(violations.isEmpty(),
            "Web-layer classes must not access Spring Data repositories directly:\n"
                + String.join("\n", violations));
    }

    private void collectViolations(Path path, List<String> violations) {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException ex) {
            throw new RuntimeException(path.toString(), ex);
        }
        String relative = WEB_SOURCE.relativize(path).toString().replace('\\', '/');
        Matcher importMatcher = REPOSITORY_IMPORT.matcher(source);
        while (importMatcher.find()) {
            violations.add(relative + ": " + importMatcher.group().trim());
        }
        Matcher fieldMatcher = REPOSITORY_FIELD.matcher(source);
        while (fieldMatcher.find()) {
            String typeName = fieldMatcher.group(1);
            if (SPRING_SECURITY_SESSION_REPOSITORY.matcher(typeName).find()) {
                continue;
            }
            violations.add(relative + ": field type " + typeName);
        }
    }
}
