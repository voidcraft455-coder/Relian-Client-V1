package com.relian.launcher.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LoaderInstallerTest {
    @TempDir Path temp;

    @Test
    void refusesToInstallLoaderBeforeVerifiedVanillaParentExists() {
        var error = assertThrows(java.io.IOException.class,
                () -> LoaderInstaller.install("Fabric", "1.21.1", temp, null, ignored -> {}));
        assertTrue(error.getMessage().contains("Install the official Minecraft 1.21.1"));
    }

    @Test
    void rejectsMismatchedVanillaParentMetadataBeforeNetworkAccess() throws Exception {
        Path version = Files.createDirectories(temp.resolve("versions/1.21.1"));
        Files.writeString(version.resolve("1.21.1.json"), "{\"id\":\"1.20.1\",\"downloads\":{\"client\":{\"sha1\":\"abc\"}}}");
        Files.writeString(version.resolve("1.21.1.jar"), "test");
        var error = assertThrows(java.io.IOException.class,
                () -> LoaderInstaller.install("Quilt", "1.21.1", temp, null, ignored -> {}));
        assertTrue(error.getMessage().contains("does not match"));
    }

    @Test
    void rejectsParentMetadataWithoutClientChecksum() throws Exception {
        Path version = Files.createDirectories(temp.resolve("versions/1.21.1"));
        Files.writeString(version.resolve("1.21.1.json"), "{\"id\":\"1.21.1\",\"downloads\":{\"client\":{}}}");
        Files.writeString(version.resolve("1.21.1.jar"), "test");
        var error = assertThrows(java.io.IOException.class,
                () -> LoaderInstaller.install("Forge", "1.21.1", temp, Path.of("java.exe"), ignored -> {}));
        assertTrue(error.getMessage().contains("missing its client checksum"));
    }
    @Test
    void rejectsClientJarWhoseContentsDoNotMatchVanillaMetadataChecksum() throws Exception {
        Path version = Files.createDirectories(temp.resolve("versions/1.21.1"));
        Files.writeString(version.resolve("1.21.1.jar"), "tampered client jar");
        String expectedSha1 = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest("expected client jar".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(version.resolve("1.21.1.json"), "{\\"id\\":\\"1.21.1\\",\\"downloads\\":{\\"client\\":{\\"sha1\\":\\""+expectedSha1+"\\"}}}");
        var error = assertThrows(java.io.IOException.class,
                () -> LoaderInstaller.install("Fabric", "1.21.1", temp, null, ignored -> {}));
        assertTrue(error.getMessage().contains("failed checksum verification"));
    }

}
