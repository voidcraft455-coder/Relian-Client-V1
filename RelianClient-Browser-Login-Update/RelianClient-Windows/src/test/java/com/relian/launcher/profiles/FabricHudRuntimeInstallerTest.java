package com.relian.launcher.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FabricHudRuntimeInstallerTest {
    @TempDir Path temp;

    private byte[] runtimeJar() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write("{\"id\":\"relian_client\",\"depends\":{\"minecraft\":\"1.21.1\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void installsBundledRuntimeBytesIntoManagedModsFolder() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        byte[] jar = runtimeJar();

        Path installed = FabricHudRuntimeInstaller.install(instance, new ByteArrayInputStream(jar));

        assertEquals("mods", installed.getParent().getFileName().toString());
        assertEquals(FabricHudRuntimeInstaller.FILE_NAME, installed.getFileName().toString());
        assertArrayEquals(jar, Files.readAllBytes(installed));
    }

    @Test
    void safelyReplacesAnEarlierManagedRuntime() throws Exception {
        Path instance = Files.createDirectory(temp.resolve("instance"));
        Path mods = Files.createDirectory(instance.resolve("mods"));
        Path destination = Files.write(mods.resolve(FabricHudRuntimeInstaller.FILE_NAME), runtimeJar());

        byte[] updated = runtimeJar();
        Path installed = FabricHudRuntimeInstaller.install(instance, new ByteArrayInputStream(updated));

        assertEquals(destination.getFileName().toString(), installed.getFileName().toString());
        assertTrue(Files.exists(destination), "The managed destination should be replaced in place.");
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
