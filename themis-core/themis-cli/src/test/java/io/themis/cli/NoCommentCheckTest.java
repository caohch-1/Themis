package io.themis.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class NoCommentCheckTest {
    @Test
    public void codeFilesContainNoComments() throws Exception {
        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get("../themis-core/src/main/java"));
        roots.add(Paths.get("../themis-static/src/main/java"));
        roots.add(Paths.get("../themis-fuzz/src/main/java"));
        roots.add(Paths.get("src/main/java"));
        roots.add(Paths.get("../themis-llm/themis_llm"));

        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile).forEach(path -> inspect(path, violations));
            }
        }
        Assertions.assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private void inspect(Path path, List<String> violations) {
        String name = path.getFileName().toString();
        if (!(name.endsWith(".java") || name.endsWith(".py"))) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            violations.add(path + ":unreadable");
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trim = line.trim();
            if (trim.startsWith("//") || trim.startsWith("/*") || trim.startsWith("*") || trim.startsWith("#")) {
                violations.add(path + ":" + (i + 1));
            }
        }
    }
}
