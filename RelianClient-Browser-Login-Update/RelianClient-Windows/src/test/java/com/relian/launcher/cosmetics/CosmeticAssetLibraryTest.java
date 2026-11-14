package com.relian.launcher.cosmetics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticAssetLibraryTest {
    @TempDir Path temp;
    private Path library() { return temp.resolve("cosmetics"); }

    private Path png(String name, int width, int height) throws Exception {
        Path file = temp.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        return file;
    }

    @Test
    void validatesAndImportsCapeWingsAndHatTextures() throws Exception {
        Path cape = CosmeticAssetLibrary.importPng(library(), CosmeticAssetLibrary.Category.CAPE, png("cape.png", 64, 32));
        Path wings = CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.WINGS, png("wings.png", 128, 64));
        Path hat = CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.HAT, png("hat.png", 128, 128));
        assertTrue(Files.isRegularFile(cape));
        assertTrue(Files.isRegularFile(wings));
        assertTrue(Files.isRegularFile(hat));
        assertEquals(1, CosmeticAssetLibrary.list(library(), CosmeticAssetLibrary.Category.CAPE).size());
        assertEquals(1, CosmeticAssetLibrary.list(CosmeticAssetLibrary.Category.WINGS).size());
        assertEquals(1, CosmeticAssetLibrary.list(CosmeticAssetLibrary.Category.HAT).size());
    }

    @Test
    void rejectsWrongDimensionsAndNonPngFiles() throws Exception {
        Path invalidCape = png("bad-cape.png", 64, 64);
        assertThrows(java.io.IOException.class,
            () -> CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.CAPE, invalidCape));
        Path text = temp.resolve("fake.png");
        Files.writeString(text, "not an image");
        assertThrows(java.io.IOException.class,
            () -> CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.HAT, text));
    }

    @Test
    void generatesUniqueNamesAndRestrictsDeletionToCategoryFolder() throws Exception {
        Path firstSource = png("same.png", 64, 32);
        Path secondSource = png("same.png", 64, 32);
        Path first = CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.CAPE, firstSource);
        Path second = CosmeticAssetLibrary.importPng(CosmeticAssetLibrary.Category.CAPE, secondSource);
        assertNotEquals(first, second);
        Path outside = png("outside.png", 64, 32);
        assertThrows(java.io.IOException.class,
            () -> CosmeticAssetLibrary.delete(library(), CosmeticAssetLibrary.Category.CAPE, outside));
        CosmeticAssetLibrary.delete(CosmeticAssetLibrary.Category.CAPE, first);
        assertEquals(1, CosmeticAssetLibrary.list(CosmeticAssetLibrary.Category.CAPE).size());
    }
}
