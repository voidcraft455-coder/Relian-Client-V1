package com.relian.launcher.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FabricHudRuntimeInstallerTest {
    @TempDir Path temp;

    @Test
    void installsBundledRuntimeBytesIntoManagedModsFolder() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        byte[] jar = new byte[] {'P', 'K', 3, 4, 1, 2, 3, 4};

        Path installed = FabricHudRuntimeInstaller.install(instance, new ByteArrayInputStream(jar));

        assertEquals("mods", installed.getParent().getFileName().toString());
        assertEquals(FabricHudRuntimeInstaller.FILE_NAME, installed.getFileName().toString());
        assertArrayEquals(jar, Files.readAllBytes(installed));
    }

    @Test
    void safelyReplacesAnEarlierManagedRuntime() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        Path mods = Files.createDirectory(instance.resolve("mods"));
        Path destination = Files.write(mods.resolve(FabricHudRuntimeInstaller.FILE_NAME),
                new byte[] {'P', 'K', 3, 4, 0});

        byte[] updated = new byte[] {'P', 'K', 3, 4, 9, 8, 7};
        Path installed = FabricHudRuntimeInstaller.install(instance, new ByteArrayInputStream(updated));

        assertEquals(destination, installed);
        assertArrayEquals(updated, Files.readAllBytes(installed));
        try (var files = Files.list(mods)) {
            assertEquals(1, files.count(), "Temporary files must be cleaned up.");
        }
    }

    @Test
    void rejectsNonJarDataAndLeavesNoPartialFile() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        assertThrows(IOException.class, () -> FabricHudRuntimeInstaller.install(instance,
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5})));
        Path mods = instance.resolve("mods");
        if (Files.exists(mods)) {
            try (var files = Files.list(mods)) { assertEquals(0, files.count()); }
        }
    }

    @Test
    void bundledRuntimeRequiresTheSupportedMinecraftAndLoader() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        assertThrows(IOException.class, () -> FabricHudRuntimeInstaller.installBundled(instance, "1.21.10", "Fabric"));
        assertThrows(IOException.class, () -> FabricHudRuntimeInstaller.installBundled(instance, "1.21.1", "Forge"));
    }
}
