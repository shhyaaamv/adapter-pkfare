package com.verteil.thirdparties.adapter.pkfare.acceptance.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes what went over the wire to {@code <outputDir>/<scenario>/}, pretty printed, so a run can be inspected
 * (and diffed against the samples) without a debugger.
 */
@Slf4j
@AllArgsConstructor
public class AcceptanceOutput {

    private final ObjectMapper objectMapper;
    private final Path outputDir;

    /** A fresh, empty directory for the scenario. */
    public Path scenarioDir(String scenarioName) {
        Path dir = outputDir.resolve(scenarioName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""));
        try {
            if (Files.isDirectory(dir)) {
                try (var files = Files.list(dir)) {
                    files.forEach(AcceptanceOutput::delete);
                }
            }
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeJson(Path dir, String fileName, JsonNode json) {
        try {
            writeText(dir, fileName, objectMapper.writeValueAsString(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeText(Path dir, String fileName, String text) {
        Path file = dir.resolve(fileName);
        try {
            Files.writeString(file, text);
            log.info("Wrote {}", file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
