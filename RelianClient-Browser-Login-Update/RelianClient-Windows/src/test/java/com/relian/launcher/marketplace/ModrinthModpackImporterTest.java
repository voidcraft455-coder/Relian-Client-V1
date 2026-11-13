package com.relian.launcher.marketplace;

import com.google.gson.JsonParser;
import com.relian.launcher.profiles.InstanceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthModpackImporterTest {
    @TempDir Path temp;

    @Test
    void parsesMinecraftLoaderAndOnlyRequiredClientFiles() throws Exception {
        String json = """
            {
              "formatVersion": 1,
              "game": "minecraft",
              "name": "Example Pack",
              "versionId": "release-4",
              "summary": "A test pack",
              "dependencies": {
                "minecraft": "1.20.1",
                "fabric-loader": "0.15.11"
              },
              "files": [
                {
                  "path": "mods/required.jar",
                  "fileSize": 0,
                  "downloads": ["https://cdn.modrinth.com/data/example/required.jar"],
                  "hashes": {},
                  "env": {"client": "required", "server": "required"}
                },
                {
                  "path": "mods/optional.jar",
                  "fileSize": 400000000,
                  "hashes": {"sha256": "not-downloaded"},
                  "env": {"client": "optional", "server": "required"}
                },
                {
                  "path": "mods/server-only.jar",
                  "hashes": {},
                  "env": {"client": "unsupported", "server": "required"}
                }
              ]
            }
            """;

        ModrinthModpackImporter.PackMetadata metadata = ModrinthModpackImporter.parseManifest(json);

        assertEquals("Example Pack", metadata.name());
        assertEquals("release-4", metadata.versionId());
        assertEquals("1.20.1", metadata.minecraftVersion());
        assertEquals("Fabric", metadata.loader());
        assertEquals("0.15.11", metadata.loaderVersion());
        assertEquals(1, metadata.clientFileCount(), "Optional and server-only files must not be downloaded by default.");
    }

    @Test
    void rejectsNonHttpsUrlsMalformedHashesAndUnsafeDestinations() {
        String insecure = manifest("""
            [{
              "path":"mods/example.jar",
              "downloads":["http://cdn.modrinth.com/example.jar"],
              "hashes":{},
              "env":{"client":"required"}
            }]
            """);
        assertThrows(IOException.class, () -> ModrinthModpackImporter.parseManifest(insecure));

        String badHash = manifest("""
            [{
              "path":"mods/example.jar",
              "downloads":["https://cdn.modrinth.com/example.jar"],
              "hashes":{"sha512":"not-a-valid-digest"},
              "env":{"client":"required"}
            }]
            """);
        assertThrows(IOException.class, () -> ModrinthModpackImporter.parseManifest(badHash));

        assertThrows(IOException.class, () -> ModrinthModpackImporter.validateGamePath("../outside.txt", false));
        assertThrows(IOException.class, () -> ModrinthModpackImporter.validateGamePath("mods/../../outside.jar", false));
        assertThrows(IOException.class, () -> ModrinthModpackImporter.validateGamePath("launcher_accounts.json", false));
        assertThrows(IOException.class, () -> ModrinthModpackImporter.validateHttpsUri("https://127.0.0.1/file.jar"));
    }

    @Test
    void rejectsUnixSymlinkAndSpecialFileModes() {
        assertTrue(ModrinthModpackImporter.isUnsafeUnixMode(0120777));
        assertTrue(ModrinthModpackImporter.isUnsafeUnixMode(0010644));
        assertFalse(ModrinthModpackImporter.isUnsafeUnixMode(0100644));
        assertFalse(ModrinthModpackImporter.isUnsafeUnixMode(0040755));
        assertFalse(ModrinthModpackImporter.isUnsafeUnixMode(0));
    }

    @Test
    void importsOverridesIntoAnIsolatedModpackInstanceWithRelianMetadata() throws Exception {
        Path archive = temp.resolve("example.mrpack");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("modrinth.index.json", manifest("[]"));
        entries.put("overrides/config/pack.json", "{\"source\":\"overrides\"}");
        entries.put("overrides/config/client.json", "{\"source\":\"overrides\"}");
        entries.put("client-overrides/config/client.json", "{\"source\":\"client-overrides\"}");
        entries.put("overrides/options.txt", "maxFps:60\nrenderDistance:4\n");
        writeArchive(archive, entries);

        Path instances = temp.resolve("instances");
        InstanceManager.CreatedInstance created =
                ModrinthModpackImporter.importPack(archive, instances, "Example Imported Pack");

        assertEquals("Example Imported Pack", created.manifest().name());
        assertEquals("1.21.1", created.manifest().minecraftVersion());
        assertEquals("Fabric", created.manifest().loader());
        assertEquals(InstanceManager.Kind.MODPACK, created.manifest().kind());
        assertFalse(created.manifest().relianModules().isEmpty());
        assertTrue(Files.isRegularFile(created.directory().resolve("relian-modules.json")));
        assertTrue(Files.isRegularFile(created.directory().resolve("options.txt")));
        assertTrue(Files.isRegularFile(created.directory().resolve("config/pack.json")));
        assertTrue(Files.isRegularFile(created.directory().resolve("config/client.json")));
        assertEquals("{\"source\":\"client-overrides\"}",
                Files.readString(created.directory().resolve("config/client.json")));
        assertTrue(Files.readString(created.directory().resolve("options.txt")).contains("maxFps:260"));
        assertTrue(Files.isRegularFile(created.directory().resolve("relian-modules.json")));
        assertTrue(Files.isRegularFile(created.directory().resolve("relian-instance.json")));

        var packMetadata = JsonParser.parseString(
                Files.readString(created.directory().resolve("relian-modpack.json"))).getAsJsonObject();
        assertEquals("Fabric", packMetadata.get("loader").getAsString());
        assertEquals("0.16.10", packMetadata.get("loaderVersion").getAsString());
        assertEquals(3, packMetadata.get("filesInstalled").getAsInt());
        assertEquals(1, InstanceManager.list(instances).size());
        assertFalse(Files.exists(created.directory().resolve("launcher_accounts.json")));
        assertFalse(Files.exists(created.directory().resolve("accounts.json")));
    }

    @Test
    void rejectsTraversalAndSymlinkArchivesWithoutPublishingAnInstance() throws Exception {
        Path traversal = temp.resolve("traversal.mrpack");
        writeArchive(traversal, Map.of(
                "modrinth.index.json", manifest("[]"),
                "overrides/../../escape.txt", "not allowed"
        ));
        Path instances = temp.resolve("instances");
        assertThrows(IOException.class,
                () -> ModrinthModpackImporter.importPack(traversal, instances, "Traversal"));
        assertFalse(Files.exists(instances.resolve("Traversal")));
        assertFalse(Files.exists(temp.resolve("escape.txt")));

        Path symlink = temp.resolve("symlink.mrpack");
        writeArchive(symlink, Map.of(
                "modrinth.index.json", manifest("[]"),
                "overrides/config/link.txt", "symlink target"
        ));
        markUnixSymlink(symlink, "overrides/config/link.txt");
        assertThrows(IOException.class,
                () -> ModrinthModpackImporter.importPack(symlink, instances, "Symlink"));
        assertFalse(Files.exists(instances.resolve("Symlink")));
        assertTrue(InstanceManager.list(instances).isEmpty());
    }

    @Test
    void importedAndRelianInstancesReceiveTheModuleManifestAndPerformanceDefaults() throws Exception {
        Path instances = temp.resolve("managed-instances");
        Path source = Files.createDirectories(temp.resolve("source-instance"));

        var imported = InstanceManager.create(instances, "Imported", "1.21.1", "Fabric",
                InstanceManager.Kind.IMPORT, source, "");
        var relian = InstanceManager.create(instances, "Relian", "1.21.1", "Fabric",
                InstanceManager.Kind.RELIAN, null, "");

        for (var created : new InstanceManager.CreatedInstance[]{imported, relian}) {
            assertFalse(created.manifest().relianModules().isEmpty());
            assertTrue(Files.isRegularFile(created.directory().resolve("relian-modules.json")));
            assertTrue(Files.isRegularFile(created.directory().resolve("options.txt")));
            assertTrue(Files.readString(created.directory().resolve("options.txt")).contains("maxFps:260"));
        }
    }

    @Test
    void rejectsOversizedArchiveEntriesBeforeCreatingAnInstance() throws Exception {
        Path archive = temp.resolve("oversized.mrpack");
        writeArchive(archive, Map.of(
                "modrinth.index.json", manifest("[]"),
                "overrides/config/large.bin", "small compressed payload"
        ));
        patchCentralUncompressedSize(archive, "overrides/config/large.bin", 256L * 1024L * 1024L + 1L);

        Path instances = temp.resolve("instances");
        assertThrows(IOException.class,
                () -> ModrinthModpackImporter.importPack(archive, instances, "Oversized"));
        assertFalse(Files.exists(instances.resolve("Oversized")));
    }

    @Test
    void refusesToOverwriteAnExistingInstanceAndKeepsVanillaFreeOfRelianModules() throws Exception {
        Path archive = temp.resolve("existing.mrpack");
        writeArchive(archive, Map.of("modrinth.index.json", manifest("[]")));
        Path instances = temp.resolve("instances");
        Path existing = Files.createDirectories(instances.resolve("Existing"));
        Path marker = Files.writeString(existing.resolve("user-data.txt"), "keep me");

        assertThrows(IOException.class,
                () -> ModrinthModpackImporter.importPack(archive, instances, "Existing"));
        assertEquals("keep me", Files.readString(marker));

        var vanilla = InstanceManager.create(instances, "Vanilla", "1.21.1", "Vanilla",
                InstanceManager.Kind.VANILLA, null, "");
        assertTrue(vanilla.manifest().relianModules().isEmpty());
        assertFalse(Files.exists(vanilla.directory().resolve("relian-modules.json")));
        assertFalse(Files.exists(vanilla.directory().resolve("options.txt")), "Vanilla must not receive Relian performance defaults.");
    }

    private static String manifest(String files) {
        return """
            {
              "formatVersion": 1,
              "game": "minecraft",
              "name": "Example Pack",
              "versionId": "1.0.0",
              "summary": "Importer test",
              "dependencies": {
                "minecraft": "1.21.1",
                "fabric-loader": "0.16.10"
              },
              "files": %s
            }
            """.formatted(files);
    }

    private static void writeArchive(Path path, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static void markUnixSymlink(Path archive, String entryName) throws IOException {
        byte[] bytes = Files.readAllBytes(archive);
        byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= bytes.length - 46 - name.length; offset++) {
            if ((bytes[offset] & 0xff) != 0x50 || (bytes[offset + 1] & 0xff) != 0x4b
                    || (bytes[offset + 2] & 0xff) != 0x01 || (bytes[offset + 3] & 0xff) != 0x02) {
                continue;
            }
            int nameLength = littleEndianShort(bytes, offset + 28);
            if (nameLength != name.length || offset + 46 + nameLength > bytes.length) continue;
            boolean matches = true;
            for (int i = 0; i < name.length; i++) {
                if (bytes[offset + 46 + i] != name[i]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;

            bytes[offset + 5] = 3; // Unix creator OS in "version made by".
            long mode = (long) 0120777 << 16;
            for (int i = 0; i < 4; i++) bytes[offset + 38 + i] = (byte) (mode >>> (8 * i));
            Files.write(archive, bytes);
            return;
        }
        fail("Could not find the ZIP central-directory entry to mark as a symlink.");
    }

    private static void patchCentralUncompressedSize(Path archive, String entryName, long size) throws IOException {
        byte[] bytes = Files.readAllBytes(archive);
        byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= bytes.length - 46 - name.length; offset++) {
            if ((bytes[offset] & 0xff) != 0x50 || (bytes[offset + 1] & 0xff) != 0x4b
                    || (bytes[offset + 2] & 0xff) != 0x01 || (bytes[offset + 3] & 0xff) != 0x02) continue;
            int nameLength = littleEndianShort(bytes, offset + 28);
            if (nameLength != name.length || offset + 46 + nameLength > bytes.length) continue;
            boolean matches = true;
            for (int i = 0; i < name.length; i++) {
                if (bytes[offset + 46 + i] != name[i]) { matches = false; break; }
            }
            if (!matches) continue;
            for (int i = 0; i < 4; i++) bytes[offset + 24 + i] = (byte) (size >>> (8 * i));
            Files.write(archive, bytes);
            return;
        }
        fail("Could not find the ZIP central-directory entry to enlarge.");
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }
}
