package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InstalledContentManagerTest {
    @TempDir Path temp;

    @Test
    void listsAndTogglesModsWithoutChangingTheirBytes() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Path jar = mods.resolve("sample-mod.jar");
        byte[] original = new byte[] {1, 2, 3, 4};
        Files.write(jar, original);

        var before = InstalledContentManager.list(temp, InstalledContentManager.Category.MODS);
        assertEquals(1, before.size());
        assertTrue(before.get(0).enabled());

        Path disabled = InstalledContentManager.toggleMod(temp, jar);
        assertEquals("sample-mod.jar.disabled", disabled.getFileName().toString());
        assertArrayEquals(original, Files.readAllBytes(disabled));
        assertFalse(InstalledContentManager.list(temp, InstalledContentManager.Category.MODS).get(0).enabled());

        Path enabled = InstalledContentManager.toggleMod(temp, disabled);
        assertEquals("sample-mod.jar", enabled.getFileName().toString());
        assertArrayEquals(original, Files.readAllBytes(enabled));
    }

    @Test
    void refusesToOverwriteAnExistingToggleTarget() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Path jar = Files.write(mods.resolve("sample.jar"), new byte[] {1});
        Files.write(mods.resolve("sample.jar.disabled"), new byte[] {2});
        assertThrows(java.io.IOException.class, () -> InstalledContentManager.toggleMod(temp, jar));
        assertTrue(Files.exists(jar));
    }

    @Test
    void listsResourcePacksAndShadersSeparately() throws Exception {
        Path resourcePacks = Files.createDirectories(temp.resolve("resourcepacks"));
        Path shaders = Files.createDirectories(temp.resolve("shaderpacks"));
        Files.write(resourcePacks.resolve("clean-ui.zip"), new byte[] {5, 6});
        Files.write(shaders.resolve("soft-light.zip"), new byte[] {7, 8, 9});

        var all = InstalledContentManager.list(temp, InstalledContentManager.Category.ALL);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(item -> item.kind() == InstalledContentManager.Kind.RESOURCE_PACK));
        assertTrue(all.stream().anyMatch(item -> item.kind() == InstalledContentManager.Kind.SHADER));
        assertEquals(1, InstalledContentManager.list(temp,
                InstalledContentManager.Category.RESOURCE_PACKS).size());
        assertEquals(1, InstalledContentManager.list(temp,
                InstalledContentManager.Category.SHADERS).size());
    }

    @Test
    void togglesResourcePacksAndShadersWithoutChangingTheirBytes() throws Exception {
        Path resourcePacks = Files.createDirectories(temp.resolve("resourcepacks"));
        Path shaders = Files.createDirectories(temp.resolve("shaderpacks"));
        byte[] packBytes = new byte[] {9, 8, 7};
        byte[] shaderBytes = new byte[] {6, 5, 4};
        Path pack = Files.write(resourcePacks.resolve("pack.zip"), packBytes);
        Path shader = Files.write(shaders.resolve("shader.zip"), shaderBytes);

        Path disabledPack = InstalledContentManager.toggleContent(temp, pack);
        Path disabledShader = InstalledContentManager.toggleContent(temp, shader);
        assertTrue(disabledPack.getFileName().toString().endsWith(".zip.disabled"));
        assertTrue(disabledShader.getFileName().toString().endsWith(".zip.disabled"));
        assertArrayEquals(packBytes, Files.readAllBytes(disabledPack));
        assertArrayEquals(shaderBytes, Files.readAllBytes(disabledShader));

        assertFalse(InstalledContentManager.list(temp,
                InstalledContentManager.Category.RESOURCE_PACKS).get(0).enabled());
        assertFalse(InstalledContentManager.list(temp,
                InstalledContentManager.Category.SHADERS).get(0).enabled());
        assertTrue(InstalledContentManager.toggleContent(temp, disabledPack).getFileName().toString().endsWith(".zip"));
        assertTrue(InstalledContentManager.toggleContent(temp, disabledShader).getFileName().toString().endsWith(".zip"));
    }

    @Test
    void refusesToToggleWhenModsFolderIsMissing() throws Exception {
        assertThrows(java.io.IOException.class,
                () -> InstalledContentManager.toggleMod(temp, temp.resolve("mods").resolve("missing.jar")));
    }

    @Test
    void rejectsFilesOutsideManagedContentFolders() throws Exception {
        Path outside = Files.write(temp.resolve("outside.jar"), new byte[] {1});
        assertThrows(java.io.IOException.class, () -> InstalledContentManager.toggleMod(temp, outside));
        assertThrows(java.io.IOException.class, () -> InstalledContentManager.delete(temp, outside));
    }
}
