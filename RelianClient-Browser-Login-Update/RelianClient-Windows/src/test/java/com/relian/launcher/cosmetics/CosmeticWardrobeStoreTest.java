package com.relian.launcher.cosmetics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticWardrobeStoreTest {
    @TempDir Path temp;

    private Path png(CosmeticAssetLibrary.Category category, String name) throws Exception {
        int width = category == CosmeticAssetLibrary.Category.HAT ? 64 : 64;
        int height = category == CosmeticAssetLibrary.Category.HAT ? 64 : 32;
        Path file = temp.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        return CosmeticAssetLibrary.importPng(temp.resolve("library"), category, file);
    }

    @Test void equipsAndPersistsSeparateWearables() throws Exception {
        Path cape = png(CosmeticAssetLibrary.Category.CAPE, "cape.png");
        Path wings = png(CosmeticAssetLibrary.Category.WINGS, "wings.png");
        CosmeticWardrobeStore.equip(temp.resolve("library"), CosmeticAssetLibrary.Category.CAPE, cape);
        CosmeticWardrobeStore.equip(temp.resolve("library"), CosmeticAssetLibrary.Category.WINGS, wings);

        assertEquals(cape.toRealPath(), CosmeticWardrobeStore.equipped(temp.resolve("library"), CosmeticAssetLibrary.Category.CAPE));
        assertEquals(wings.toRealPath(), CosmeticWardrobeStore.equipped(temp.resolve("library"), CosmeticAssetLibrary.Category.WINGS));
        assertEquals(2, CosmeticWardrobeStore.loadout(temp.resolve("library")).size());
    }

    @Test void unequipsWithoutDeletingTheAsset() throws Exception {
        Path hat = png(CosmeticAssetLibrary.Category.HAT, "hat.png");
        CosmeticWardrobeStore.equip(temp.resolve("library"), CosmeticAssetLibrary.Category.HAT, hat);
        CosmeticWardrobeStore.unequip(temp.resolve("library"), CosmeticAssetLibrary.Category.HAT);

        assertNull(CosmeticWardrobeStore.equipped(temp.resolve("library"), CosmeticAssetLibrary.Category.HAT));
        assertTrue(Files.exists(hat));
    }

    @Test void rejectsAssetsOutsideTheirCategoryAndSkinAsWearable() throws Exception {
        Path outside = temp.resolve("outside.png");
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", outside.toFile());
        assertThrows(java.io.IOException.class, () -> CosmeticWardrobeStore.equip(
                temp.resolve("library"), CosmeticAssetLibrary.Category.CAPE, outside));
        assertThrows(java.io.IOException.class, () -> CosmeticWardrobeStore.unequip(
                temp.resolve("library"), CosmeticAssetLibrary.Category.SKIN));
    }
    @Test void ignoresTamperedWardrobePaths() throws Exception {
        Path library = temp.resolve("library");
        Path cape = png(CosmeticAssetLibrary.Category.CAPE, "safe-cape.png");
        CosmeticWardrobeStore.equip(library, CosmeticAssetLibrary.Category.CAPE, cape);
        Path state = library.resolve("wardrobe.json");
        Files.writeString(state, """
                {"CAPE":"capes/../../outside.png","WINGS":"wings/../outside.png","HAT":"hats/"}
                """);
        assertNull(CosmeticWardrobeStore.equipped(library, CosmeticAssetLibrary.Category.CAPE));
        assertNull(CosmeticWardrobeStore.equipped(library, CosmeticAssetLibrary.Category.WINGS));
        assertNull(CosmeticWardrobeStore.equipped(library, CosmeticAssetLibrary.Category.HAT));
    }

    @Test void refusesToOverwriteWardrobeSymlink() throws Exception {
        Path library = temp.resolve("library");
        Files.createDirectories(library);
        Path outside = temp.resolve("outside.json");
        Files.writeString(outside, "{}");
        Path wardrobe = library.resolve("wardrobe.json");
        try {
            Files.createSymbolicLink(wardrobe, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            return; // Symlink creation may be unavailable on Windows without developer mode.
        }
        Path cape = png(CosmeticAssetLibrary.Category.CAPE, "cape-for-link.png");
        assertThrows(java.io.IOException.class, () -> CosmeticWardrobeStore.equip(
                library, CosmeticAssetLibrary.Category.CAPE, cape));
        assertEquals("{}", Files.readString(outside));
    }

}
