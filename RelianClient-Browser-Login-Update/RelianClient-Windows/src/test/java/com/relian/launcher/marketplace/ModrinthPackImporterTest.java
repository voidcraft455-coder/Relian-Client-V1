package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthPackImporterTest {
    @TempDir Path temporary;

    @Test
    void rejectsPathTraversalBeforeWritingOutsideInstance() throws Exception {
        Path instance = instance("MODPACK");
        Path archive = temporary.resolve("traversal.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "modrinth.index.json", index("fabric-loader", "0.16.0"));
            put(zip, "overrides/../outside.txt", "must not escape");
        }

        assertThrows(IOException.class, () -> ModrinthPackImporter.importPack(
                archive, instance, "1.21.1", "Fabric"));
        assertFalse(Files.exists(temporary.resolve("outside.txt")));
        assertFalse(Files.exists(instance.resolve("outside.txt")));
        assertTrue(Files.isDirectory(instance));
    }

    @Test
    void rejectsLoaderMismatchFromManifest() throws Exception {
        Path instance = instance("MODPACK");
        Path archive = temporary.resolve("loader-mismatch.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "modrinth.index.json", index("fabric-loader", "0.16.0"));
        }

        IOException error = assertThrows(IOException.class, () -> ModrinthPackImporter.importPack(
                archive, instance, "1.21.1", "Forge"));
        assertTrue(error.getMessage().contains("requires the fabric loader"));
    }

    @Test
    void importsClientOverridesAndWritesPackMetadata() throws Exception {
        Path instance = instance("MODPACK");
        Path archive = temporary.resolve("valid.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "modrinth.index.json", index("fabric-loader", "0.16.0"));
            put(zip, "overrides/config/relian-test.cfg", "enabled=true");
        }

        ModrinthPackImporter.ImportResult result = ModrinthPackImporter.importPack(
                archive, instance, "1.21.1", "Fabric");
        assertTrue(result.packName().equals("Test Pack"));
        assertTrue(Files.readString(instance.resolve("config/relian-test.cfg")).equals("enabled=true"));
        assertTrue(Files.readString(instance.resolve("relian-modpack.json")).contains("\"minecraftVersion\":\"1.21.1\""));
    }

    @Test
    void neverOverwritesExistingInstanceFiles() throws Exception {
        Path instance = instance("MODPACK");
        Path existing = Files.createDirectories(instance.resolve("config")).resolve("user.cfg");
        Files.writeString(existing, "keep this user file");
        Path archive = temporary.resolve("overwrite.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "modrinth.index.json", index("fabric-loader", "0.16.0"));
            put(zip, "overrides/config/user.cfg", "replace attempt");
        }

        assertThrows(IOException.class, () -> ModrinthPackImporter.importPack(
                archive, instance, "1.21.1", "Fabric"));
        assertTrue(Files.readString(existing).equals("keep this user file"));
    }

    @Test
    void refusesToImportIntoVanillaInstance() throws Exception {
        Path instance = instance("VANILLA");
        Path archive = temporary.resolve("empty.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "modrinth.index.json", index("fabric-loader", "0.16.0"));
        }

        IOException error = assertThrows(IOException.class, () -> ModrinthPackImporter.importPack(
                archive, instance, "1.21.1", "Fabric"));
        assertTrue(error.getMessage().contains("cannot be imported into a Vanilla instance"));
    }

    private Path instance(String kind) throws IOException {
        Path directory = Files.createDirectory(temporary.resolve("instance-" + kind));
        Files.writeString(directory.resolve("relian-instance.json"), "{\"kind\":\"" + kind + "\"}");
        return directory;
    }

    private static String index(String loaderKey, String loaderVersion) {
        return "{\"formatVersion\":1,\"game\":\"minecraft\",\"name\":\"Test Pack\","
                + "\"versionId\":\"test-1\",\"dependencies\":{\"minecraft\":\"1.21.1\","
                + "\"" + loaderKey + "\":\"" + loaderVersion + "\"},\"files\":[]}";
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
