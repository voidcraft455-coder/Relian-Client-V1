package com.relian.launcher.cosmetics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkinLibraryManagerTest {
    @TempDir Path temp;

    private Path png(String name, int width, int height) throws Exception {
        Path file = temp.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        return file;
    }

    @Test
    void importsValidModernAndLegacySkinDimensions() throws Exception {
        Path library = temp.resolve("skins");
        Path modern = SkinLibraryManager.importSkin(library, png("Modern.png", 64, 64));
        Path legacy = SkinLibraryManager.importSkin(library, png("Legacy.png", 64, 32));

        assertTrue(Files.isRegularFile(modern));
        assertTrue(Files.isRegularFile(legacy));
        assertEquals(2, SkinLibraryManager.list(library).size());
    }

    @Test
    void rejectsImagesThatAreNotMinecraftSkinDimensions() throws Exception {
        Path invalid = png("wallpaper.png", 128, 128);
        assertThrows(java.io.IOException.class, () -> SkinLibraryManager.importSkin(temp.resolve("skins"), invalid));
    }

    @Test
    void createsUniqueNamesAndDeletesOnlyManagedSkin() throws Exception {
        Path library = temp.resolve("skins");
        Path first = SkinLibraryManager.importSkin(library, png("My Skin.png", 64, 64));
        Path second = SkinLibraryManager.importSkin(library, png("My Skin.png", 64, 64));
        assertNotEquals(first, second);
        assertEquals(2, SkinLibraryManager.list(library).size());

        Path outside = png("outside.png", 64, 64);
        assertThrows(java.io.IOException.class, () -> SkinLibraryManager.delete(library, outside));
        SkinLibraryManager.delete(library, first);
        assertEquals(1, SkinLibraryManager.list(library).size());
    }
}
