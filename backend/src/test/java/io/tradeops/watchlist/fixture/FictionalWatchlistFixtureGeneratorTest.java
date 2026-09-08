package io.tradeops.watchlist.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class FictionalWatchlistFixtureGeneratorTest {

    @Test
    void generatedCsvPayloadsMatchCommittedFictionalResources() throws IOException {
        Map<String, String> generated = FictionalWatchlistFixtureGenerator.generateCsvByVersion();

        assertEquals(2, generated.size());
        for (Map.Entry<String, String> fixture : generated.entrySet()) {
            assertEquals(readResource("fixtures/watchlist/" + fixture.getKey() + ".csv"), fixture.getValue());
            assertEquals(5, fixture.getValue().lines().count());
            fixture.getValue().lines().skip(1).forEach(row -> {
                String[] columns = row.split(",", -1);
                assertEquals(9, columns.length);
                assertEquals(FictionalWatchlistFixtureGenerator.PROVIDER, columns[0]);
                assertEquals(fixture.getKey(), columns[1]);
                assertEquals("FICTIONAL", columns[8]);
            });
        }
    }

    @Test
    void generatedXmlPayloadsMatchCommittedFictionalResourcesAndRemainParseable() throws Exception {
        Map<String, String> generated = FictionalWatchlistFixtureGenerator.generateXmlByVersion();

        assertEquals(2, generated.size());
        for (Map.Entry<String, String> fixture : generated.entrySet()) {
            assertEquals(readResource("fixtures/watchlist/" + fixture.getKey() + ".xml"), fixture.getValue());
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(fixture.getValue().getBytes(StandardCharsets.UTF_8)));
            assertEquals("watchlist", document.getDocumentElement().getTagName());
            assertEquals(FictionalWatchlistFixtureGenerator.PROVIDER, document.getDocumentElement().getAttribute("provider"));
            assertEquals(fixture.getKey(), document.getDocumentElement().getAttribute("version"));
            assertEquals("FICTIONAL", document.getDocumentElement().getAttribute("dataOrigin"));
            assertEquals(4, document.getElementsByTagName("entity").getLength());
        }
    }

    @Test
    void writesTheSameDeterministicPayloadsToTheRequestedDirectory() throws IOException {
        Path output = Files.createTempDirectory("tradeops-fictional-fixtures-");
        try {
            FictionalWatchlistFixtureGenerator.writeTo(output);
            for (Map.Entry<String, String> fixture : FictionalWatchlistFixtureGenerator.generateCsvByVersion().entrySet()) {
                assertEquals(fixture.getValue(), Files.readString(output.resolve(fixture.getKey() + ".csv")));
            }
            for (Map.Entry<String, String> fixture : FictionalWatchlistFixtureGenerator.generateXmlByVersion().entrySet()) {
                assertEquals(fixture.getValue(), Files.readString(output.resolve(fixture.getKey() + ".xml")));
            }
        } finally {
            try (var files = Files.list(output)) {
                files.forEach(path -> path.toFile().delete());
            }
            output.toFile().delete();
        }
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test fixture resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}