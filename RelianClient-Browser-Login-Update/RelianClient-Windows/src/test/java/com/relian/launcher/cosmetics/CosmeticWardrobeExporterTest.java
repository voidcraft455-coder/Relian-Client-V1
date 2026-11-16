package com.reliant.launcher.cosmetics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticWardrobeExporterTest {
    @TempDir Path temp;

    private Path importAsset(CosmeticAssetLibrary.Category category, String name) throws Exception {
        Path source = temp.resolve(name);
        int height = category == CosmeticAssetLibrary.Category.HAT ? 64 : 32;
        ImageIO.write(new BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB), "png", source.toFile());
        return CosmeticAssetLibrary.importPng(temp.resolve("library"), category, source);
    }

    @Test void exportsEquippedWearablesIntoSelectedInstance() throws Exception {
        Path cape = importAsset(CosmeticAssetLibrary.Category.CAPE, "export-cape.png");
        Path hat = importAsset(CosmeticAssetLibrary.Category.HAT, "export-hat.png");
        CosmeticWardrobeStore.equip(temp.resolve("library"), CosmeticAssetLibrary.Category.CAPE, cape);
        CosmeticWardrobeStore.equip(temp.resolve("library"), CosmeticAssetLibrary.Category.HAT, hat);
        Path instance = temp.resolve("instance");
        Files.createDirectories(instance);

        Path exported = CosmeticWardrobeExporter.export(instance, temp.resolve("library"));

        assertTrue(Files.isRegularFile(exported.resolve("capes/export-cape.png")));
        assertTrue(Files.isRegularFile(exported.resolve("hats/export-hat.png")));
        String manifest = Files.readString(exported.resolve("wardrobe.json"));
        assertTrue(manifest.contains("capes/export-cape.png"));
        assertTrue(manifest.contains("hats/export-hat.png"));
        assertFalse(manifest.contains("WINGS"));
    }

    @Test void rejectsMissingOrUnsafeInstanceTargets() throws Exception {
        assertThrows(java.io.IOException.class, () -> CosmeticWardrobeExporter.export(null));
        Path file = temp.resolve("not-a-folder");
        Files.writeString(file, "x");
        assertThrows(java.io.IOException.class, () -> CosmeticWardrobeExporter.export(file));
    }
}
