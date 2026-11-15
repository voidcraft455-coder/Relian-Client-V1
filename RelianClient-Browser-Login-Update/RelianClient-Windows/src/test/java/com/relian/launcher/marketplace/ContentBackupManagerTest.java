package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentBackupManagerTest {
    @TempDir Path temp;

    @Test
    void createsListsAndRestoresBackupWithoutOverwritingExistingFiles() throws Exception {
        Path root = Files.createDirectory(temp.resolve("game"));
        Path mods = Files.createDirectories(root.resolve("mods"));
        Path packs = Files.createDirectories(root.resolve("resourcepacks"));
        byte[] modBytes = {1, 2, 3, 4};
        byte[] packBytes = {9, 8, 7};
        Files.write(mods.resolve("sample.jar"), modBytes);
        Files.write(packs.resolve("visual.zip"), packBytes);

        Path backup = ContentBackupManager.createBackup(root);
        assertTrue(Files.isRegularFile(backup));
        assertEquals(1, ContentBackupManager.listBackups(root).size());

        Files.delete(mods.resolve("sample.jar"));
        Files.delete(packs.resolve("visual.zip"));
        List<Path> restored = ContentBackupManager.restoreBackup(root, backup);
        assertEquals(2, restored.size());
        assertArrayEquals(modBytes, Files.readAllBytes(mods.resolve("sample.jar")));
        assertArrayEquals(packBytes, Files.readAllBytes(packs.resolve("visual.zip")));
    }

    @Test
    void refusesRestoreWhenAnyDestinationAlreadyExists() throws Exception {
        Path root = Files.createDirectory(temp.resolve("game"));
        Path mods = Files.createDirectories(root.resolve("mods"));
        Path original = Files.write(mods.resolve("sample.jar"), new byte[] {1, 2});
        Path backup = ContentBackupManager.createBackup(root);
        Files.write(original, new byte[] {7, 7, 7});
        assertThrows(java.io.IOException.class, () -> ContentBackupManager.restoreBackup(root, backup));
        assertArrayEquals(new byte[] {7, 7, 7}, Files.readAllBytes(original));
    }

    @Test
    void rejectsBackupOutsideManagedBackupDirectory() throws Exception {
        Path root = Files.createDirectory(temp.resolve("game"));
        Path outside = Files.write(temp.resolve("outside.zip"), new byte[] {1});
        assertThrows(java.io.IOException.class, () -> ContentBackupManager.restoreBackup(root, outside));
    }
}