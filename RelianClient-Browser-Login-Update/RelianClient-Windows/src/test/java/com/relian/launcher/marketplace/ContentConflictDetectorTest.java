package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ContentConflictDetectorTest {
    @TempDir Path temp;

    @Test void rejectsDuplicateFabricModId() throws Exception {
        Path mods = Files.createDirectory(temp.resolve("mods"));
        writeJar(mods.resolve("first.jar"), "fabric.mod.json", "{\"id\":\"example_mod\"}");
        Path candidate = temp.resolve("candidate.jar");
        writeJar(candidate, "fabric.mod.json", "{\"id\":\"example_mod\"}");
        IOException error = assertThrows(IOException.class,
                () -> ContentConflictDetector.ensureNoDuplicateModIds(mods, candidate));
        assertTrue(error.getMessage().contains("example_mod"));
    }

    @Test void detectsQuiltMetadataDuplicates() throws Exception {
        Path mods = Files.createDirectory(temp.resolve("mods"));
        writeJar(mods.resolve("existing.jar"), "quilt.mod.json", "{\"id\":\"quilt_example\"}");
        Path candidate = temp.resolve("candidate.jar");
        writeJar(candidate, "quilt.mod.json", "{\"id\":\"quilt_example\"}");
        assertThrows(IOException.class, () -> ContentConflictDetector.ensureNoDuplicateModIds(mods, candidate));
    }

    @Test void permitsDifferentModIds() throws Exception {
        Path mods = Files.createDirectory(temp.resolve("mods"));
        writeJar(mods.resolve("first.jar"), "fabric.mod.json", "{\"id\":\"first_mod\"}");
        Path candidate = temp.resolve("candidate.jar");
        writeJar(candidate, "fabric.mod.json", "{\"id\":\"second_mod\"}");
        assertDoesNotThrow(() -> ContentConflictDetector.ensureNoDuplicateModIds(mods, candidate));
    }

    private static void writeJar(Path path, String entryName, String content) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
