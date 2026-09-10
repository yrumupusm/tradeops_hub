package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DocumentationLanguageContractTest {

    private static final Path KO = Path.of("docs/ko");
    private static final Path EN = Path.of("docs/en");
    private static final Pattern FENCE = Pattern.compile("(?ms)^```([^\\r\\n]*)\\R(.*?)^```[ \\t]*$");
    private static final Pattern LINK = Pattern.compile("(?<!!)\\[[^\\]\\r\\n]*\\]\\(([^)\\r\\n]+)\\)");
    private static final Set<String> SHARED_EXAMPLE_TYPES = Set.of("json", "properties", "powershell", "bash", "markdown");

    @Test
    void allPublicDocumentsHaveIndexedLanguagePairsAndSwitchLinks() throws IOException {
        Set<String> korean = relativeMarkdownFiles(KO);
        Set<String> english = relativeMarkdownFiles(EN);
        assertThat(korean).hasSizeGreaterThanOrEqualTo(21).isEqualTo(english);
        String index = read(Path.of("docs/README.md"));

        for (String relative : korean) {
            assertThat(index).contains("ko/" + relative, "en/" + relative);
            String prefix = "../".repeat(relative.split("/").length);
            for (Path root : List.of(KO, EN)) {
                String body = read(root.resolve(relative));
                assertThat(body).as(root + "/" + relative)
                        .contains("[한국어](" + prefix + "ko/" + relative + ")")
                        .contains("[English](" + prefix + "en/" + relative + ")");
                assertThat(body.lines().count()).as("Full document: " + root + "/" + relative).isGreaterThan(8);
            }
        }
    }

    @Test
    void translationsPreserveExecutableAndSourceFormatExamples() throws IOException {
        for (String relative : relativeMarkdownFiles(KO)) {
            assertThat(sharedExamples(read(KO.resolve(relative))))
                    .as("Unchanged commands, configuration, JSON and source examples: " + relative)
                    .isEqualTo(sharedExamples(read(EN.resolve(relative))));
        }
    }

    @Test
    void documentationLinksResolveAndFencesAreBalanced() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(Path.of("docs"))) {
            files.addAll(stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".md")).toList());
        }
        files.addAll(List.of(Path.of("README.md"), Path.of("PROJECT_GUIDE.md"), Path.of("AGENTS.md")));
        for (Path file : files) {
            String body = read(file);
            assertThat(body.lines().filter(line -> line.startsWith("```")).count() % 2)
                    .as("Balanced code fences: " + file).isZero();
            var links = LINK.matcher(FENCE.matcher(body).replaceAll(""));
            while (links.find()) {
                String target = links.group(1);
                if (target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || target.startsWith("#")) {
                    continue;
                }
                String local = target.split("#", 2)[0];
                Path parent = file.toAbsolutePath().getParent();
                Path resolved = parent.resolve(local).normalize();
                assertThat(Files.exists(resolved)).as(file + " -> " + target).isTrue();
            }
        }
    }

    @Test
    void legacyPathsOnlyRedirectAndWorkingGuidanceUsesEnglishReferences() throws IOException {
        for (String relative : relativeMarkdownFiles(EN)) {
            if (relative.equals("README.md")) {
                continue;
            }
            String legacy = read(Path.of("docs").resolve(relative));
            assertThat(legacy).contains("Document moved", "ko/" + relative, "en/" + relative);
            assertThat(legacy.lines().count()).isLessThan(12);
        }
        assertThat(read(Path.of("AGENTS.md"))).contains("docs/en/", "docs/ko/", "same commit");
        assertThat(read(Path.of("PROJECT_GUIDE.md"))).contains("docs/en/README.md", "researchAreas");
        assertThat(read(Path.of("README.md"))).contains("docs/ko/api-contract.md", "docs/README.md");
    }

    private static Set<String> relativeMarkdownFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return new TreeSet<>(stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/')).toList());
        }
    }

    private static List<String> sharedExamples(String body) {
        List<String> examples = new ArrayList<>();
        var fences = FENCE.matcher(body);
        while (fences.find()) {
            if (SHARED_EXAMPLE_TYPES.contains(fences.group(1).trim())) {
                examples.add(fences.group(1).trim() + "\n" + fences.group(2).strip());
            }
        }
        return examples;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
