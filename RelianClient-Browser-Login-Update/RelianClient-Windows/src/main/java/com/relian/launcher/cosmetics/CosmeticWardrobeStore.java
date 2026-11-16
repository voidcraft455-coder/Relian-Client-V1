package com.relian.launcher.cosmetics;

import com.google.gson.Gson;
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

/** Persistent local selections for wearable cosmetic assets. Does not claim server or in-game visibility. */
public final class CosmeticWardrobeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "wardrobe.json";
    private CosmeticWardrobeStore() {}

    public static synchronized void equip(CosmeticAssetLibrary.Category category, Path asset) throws IOException {
        equip(CosmeticAssetLibrary.root(), category, asset);
    }

    public static synchronized void equip(Path libraryRoot, CosmeticAssetLibrary.Category category, Path asset) throws IOException {
        requireWearable(category);
        Path folder = CosmeticAssetLibrary.directory(libraryRoot, category);
        Path candidate = checkedAsset(folder, asset);
        JsonObject state = read(libraryRoot);
        state.addProperty(category.name(), category.folder + "/" + candidate.getFileName());
        write(libraryRoot, state);
    }

    public static synchronized void unequip(CosmeticAssetLibrary.Category category) throws IOException {
        unequip(CosmeticAssetLibrary.root(), category);
    }

    public static synchronized void unequip(Path libraryRoot, CosmeticAssetLibrary.Category category) throws IOException {
        requireWearable(category);
        JsonObject state = read(libraryRoot);
        state.remove(category.name());
        write(libraryRoot, state);
    }

    public static synchronized Path equipped(CosmeticAssetLibrary.Category category) throws IOException {
        return equipped(CosmeticAssetLibrary.root(), category);
    }

    public static synchronized Path equipped(Path libraryRoot, CosmeticAssetLibrary.Category category) throws IOException {
        requireWearable(category);
        JsonObject state = read(libraryRoot);
        if (!state.has(category.name()) || !state.get(category.name()).isJsonPrimitive()) return null;
        if (!state.get(category.name()).getAsJsonPrimitive().isString()) return null;
        String relative = state.get(category.name()).getAsString();
        String prefix = category.folder + "/";
        if (!relative.startsWith(prefix)) return null;
        String fileName = relative.substring(prefix.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..")
                || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) return null;
        Path folder = CosmeticAssetLibrary.directory(libraryRoot, category);
        Path candidate = folder.resolve(fileName).normalize();
        if (!candidate.getParent().equals(folder) || Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return null;
        return candidate.toRealPath();
    }

    public static synchronized Map<CosmeticAssetLibrary.Category, Path> loadout(Path libraryRoot) throws IOException {
        Map<CosmeticAssetLibrary.Category, Path> result = new EnumMap<>(CosmeticAssetLibrary.Category.class);
        for (CosmeticAssetLibrary.Category category : new CosmeticAssetLibrary.Category[] {
                CosmeticAssetLibrary.Category.CAPE, CosmeticAssetLibrary.Category.WINGS, CosmeticAssetLibrary.Category.HAT }) {
            Path asset = equipped(libraryRoot, category);
            if (asset != null) result.put(category, asset);
        }
        return Map.copyOf(result);
    }

    private static void requireWearable(CosmeticAssetLibrary.Category category) throws IOException {
        if (category != CosmeticAssetLibrary.Category.CAPE && category != CosmeticAssetLibrary.Category.WINGS
                && category != CosmeticAssetLibrary.Category.HAT) {
            throw new IOException("Only cape, wings, and hat assets can be selected in the local wardrobe.");
        }
    }

    private static Path checkedAsset(Path folder, Path asset) throws IOException {
        if (asset == null || Files.isSymbolicLink(asset) || !Files.isRegularFile(asset, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Choose an existing regular cosmetic PNG.");
        Path candidate = asset.toAbsolutePath().normalize();
        if (!folder.equals(candidate.getParent()) || !candidate.getFileName().toString().toLowerCase().endsWith(".png")
                || !candidate.toRealPath().getParent().equals(folder)) {
            throw new IOException("The selected cosmetic must belong to its matching Relian library.");
        }
        return candidate.toRealPath();
    }

    private static JsonObject read(Path libraryRoot) throws IOException {
        Path base = libraryRoot.toAbsolutePath().normalize();
        Files.createDirectories(base);
        if (Files.isSymbolicLink(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Wardrobe library must be a regular directory.");
        Path file = base.resolve(FILE);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new JsonObject();
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Wardrobe data file is not a regular file.");
        try {
            var parsed = com.google.gson.JsonParser.parseString(Files.readString(file));
            if (!parsed.isJsonObject()) throw new IOException("Wardrobe data has an invalid format.");
            return parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Could not read wardrobe selections.", ex);
        }
    }

    private static void write(Path libraryRoot, JsonObject state) throws IOException {
        if (libraryRoot == null) throw new IOException("A wardrobe library folder is required.");
        Path base = libraryRoot.toAbsolutePath().normalize();
        if (Files.exists(base, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("Wardrobe library must be a regular directory.");
        Files.createDirectories(base);
        if (Files.isSymbolicLink(base)) throw new IOException("Wardrobe library cannot be a symbolic link.");
        base = base.toRealPath();
        Path target = base.resolve(FILE);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("Wardrobe data file is not a regular file.");
        Path temporary = Files.createTempFile(base, ".wardrobe-", ".part");
        try {
            Files.writeString(temporary, GSON.toJson(state));
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
