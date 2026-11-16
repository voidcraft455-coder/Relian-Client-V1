package com.reliant.launcher.cosmetics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.EnumMap;
import java.util.Map;

/** Copies the selected local wardrobe into an instance-scoped bundle for client-side integrations. */
public final class CosmeticWardrobeExporter {
    private CosmeticWardrobeExporter() {}

    public static Path export(Path instanceDirectory) throws IOException {
        return export(instanceDirectory, CosmeticAssetLibrary.root());
    }

    public static Path export(Path instanceDirectory, Path libraryRoot) throws IOException {
        if (instanceDirectory == null) throw new IOException("Choose an instance before exporting cosmetics.");
        Path instance = instanceDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(instance) || !Files.isDirectory(instance, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The selected instance must be an existing regular directory.");
        Path realInstance = instance.toRealPath();
        Path config = realInstance.resolve("config");
        if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(config) || !Files.isDirectory(config, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("The instance config path is not a safe directory.");
        Files.createDirectories(config);
        Path relian = config.resolve("relian");
        if (Files.exists(relian, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(relian) || !Files.isDirectory(relian, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("The Relian config path is not a safe directory.");
        Files.createDirectories(relian);
        Path target = relian.resolve("cosmetics");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("The instance cosmetics path is not a safe directory.");
        Files.createDirectories(target);
        target = target.toRealPath();
        if (!target.startsWith(realInstance)) throw new IOException("Cosmetics export escaped the selected instance.");

        JsonObject manifest = new JsonObject();
        Map<CosmeticAssetLibrary.Category, Path> loadout =
                CosmeticWardrobeStore.loadout(libraryRoot);
        for (CosmeticAssetLibrary.Category category : new CosmeticAssetLibrary.Category[] {
                CosmeticAssetLibrary.Category.CAPE, CosmeticAssetLibrary.Category.WINGS,
                CosmeticAssetLibrary.Category.HAT }) {
            Path source = loadout.get(category);
            if (source == null) continue;
            if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("A selected " + category.label.toLowerCase() + " asset is no longer available.");
            Path categoryFolder = target.resolve(category.folder);
            if (Files.exists(categoryFolder, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(categoryFolder) || !Files.isDirectory(categoryFolder, LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Unsafe " + category.label.toLowerCase() + " export folder.");
            Files.createDirectories(categoryFolder);
            Path destination = categoryFolder.resolve(source.getFileName().toString()).normalize();
            if (!destination.getParent().equals(categoryFolder)) throw new IOException("Invalid cosmetic filename.");
            Path temporary = Files.createTempFile(categoryFolder, ".relian-export-", ".part");
            try {
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                move(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            manifest.addProperty(category.name(), category.folder + "/" + destination.getFileName());
        }
        manifest.addProperty("format", 1);
        manifest.addProperty("source", "Relian local wardrobe");
        Path manifestFile = target.resolve("wardrobe.json");
        Path temporaryManifest = Files.createTempFile(target, ".wardrobe-", ".part");
        try {
            Files.writeString(temporaryManifest, new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
            move(temporaryManifest, manifestFile);
        } finally {
            Files.deleteIfExists(temporaryManifest);
        }
        return target;
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
