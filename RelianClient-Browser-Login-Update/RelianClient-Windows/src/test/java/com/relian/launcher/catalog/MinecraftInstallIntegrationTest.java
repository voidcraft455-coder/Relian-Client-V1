package com.relian.launcher.catalog;

import com.relian.launcher.runtime.JavaRuntimeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Network-backed installation smoke test, enabled explicitly by the Windows CI workflow. */
class MinecraftInstallIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void javaRequirementsMatchSupportedMinecraftGenerations() {
        assertEquals(8, JavaRuntimeManager.requiredMajor("1.8.9"));
        assertEquals(16, JavaRuntimeManager.requiredMajor("1.17.1"));
        assertEquals(17, JavaRuntimeManager.requiredMajor("1.18.2"));
        assertEquals(17, JavaRuntimeManager.requiredMajor("1.20.4"));
        assertEquals(21, JavaRuntimeManager.requiredMajor("1.20.5"));
        assertEquals(21, JavaRuntimeManager.requiredMajor("1.21.1"));
        assertEquals(25, JavaRuntimeManager.requiredMajor("26.3"));
    }

    @Test
    @EnabledIfSystemProperty(named = "relian.minecraft.integration", matches = "true")
    void installsOfficialMinecraft1211FilesAndVerifiesThem() throws Exception {
        MinecraftVersionCatalog catalog = new MinecraftVersionCatalog();
        MinecraftRelease release = catalog.fetchAllReleases().stream()
                .filter(item -> item.id().equals("1.21.1"))
                .findFirst().orElseThrow(() -> new AssertionError("Minecraft 1.21.1 is missing from Mojang's manifest."));

        MinecraftVersionInstaller.InstallResult result =
                MinecraftVersionInstaller.install(release, temporaryDirectory, ignored -> {});

        assertTrue(Files.isRegularFile(result.metadataFile()));
        assertTrue(Files.isRegularFile(result.clientJar()));
        assertTrue(Files.size(result.clientJar()) > 1_000_000);
        assertTrue(result.libraries() > 0, "Expected verified Minecraft libraries.");
        assertTrue(result.nativeJars() > 0, "Expected Windows native libraries.");
        assertTrue(result.assets() > 0, "Expected Minecraft assets.");
        try (var indexes = Files.list(temporaryDirectory.resolve("assets/indexes"))) {
            assertTrue(indexes.findAny().isPresent(), "Expected a downloaded asset index.");
        }
    }
}
