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
        assertEquals(jar, enabled);
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
    void rejectsFilesOutsideManagedContentFolders() throws Exception {
        Path outside = Files.write(temp.resolve("outside.jar"), new byte[] {1});
        assertThrows(java.io.IOException.class, () -> InstalledContentManager.toggleMod(temp, outside));
        assertThrows(java.io.IOException.class, () -> InstalledContentManager.delete(temp, outside));
    }
}
