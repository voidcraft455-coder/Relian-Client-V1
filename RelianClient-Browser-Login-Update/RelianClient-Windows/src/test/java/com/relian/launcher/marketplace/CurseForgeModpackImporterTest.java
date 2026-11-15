package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CurseForgeModpackImporterTest {
    @TempDir Path temporary;

    @Test
    void requiresApiKeyBeforeImport() {
        Path archive = temporary.resolve("pack.zip");
        assertDoesNotThrow(() -> Files.writeString(archive, "not a zip"));
        IOException error = assertThrows(IOException.class,
                () -> CurseForgeModpackImporter.importPack(archive, temporary.resolve("instances"), "Test Pack", ""));
        assertTrue(error.getMessage().contains("RELIAN_CURSEFORGE_API_KEY"));
    }

    @Test
    void rejectsNonZipArchives() throws Exception {
        Path archive = temporary.resolve("pack.mrpack");
        Files.writeString(archive, "not a zip");
        IOException error = assertThrows(IOException.class,
                () -> CurseForgeModpackImporter.importPack(archive, temporary.resolve("instances"), "Test Pack", "test-key"));
        assertTrue(error.getMessage().contains("CurseForge modpack ZIP"));
    }
}
