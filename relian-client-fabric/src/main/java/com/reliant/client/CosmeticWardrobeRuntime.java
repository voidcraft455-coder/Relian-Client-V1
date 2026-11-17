package com.reliant.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and safely uploads the instance-scoped wardrobe PNGs as client textures. */
final class CosmeticWardrobeRuntime {
    private static final long MAX_ASSET_BYTES = 8L * 1024L * 1024L;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("relian").resolve("cosmetics").toAbsolutePath().normalize();
    private static volatile long lastRead;
    private static volatile Map<String, Path> cachedAssets = Map.of();
    private static final Map<String, CachedTexture> textures = new HashMap<>();

    private record CachedTexture(Path path, long modified, Identifier id) {}

    private CosmeticWardrobeRuntime() {}

    static List<String> lines() {
        Map<String, Path> assets = assets();
        List<String> result = new ArrayList<>();
        assets.forEach((slot, path) -> result.add(slot + ": " + path.getFileName()));
        return List.copyOf(result);
    }

    private static Map<String, Path> assets() {
        long now = System.currentTimeMillis();
        if (now - lastRead < 1500L) return cachedAssets;
        synchronized (CosmeticWardrobeRuntime.class) {
            if (now - lastRead < 1500L) return cachedAssets;
            cachedAssets = readSafely();
            lastRead = now;
            return cachedAssets;
        }
    }

    /** Returns a registered GPU texture for an equipped PNG, or null when the slot is empty/invalid. */
    static synchronized Identifier texture(MinecraftClient client, String slot) {
        Path asset = assets().get(slot);
        if (asset == null) return null;
        try {
            long modified = Files.getLastModifiedTime(asset, LinkOption.NOFOLLOW_LINKS).toMillis();
            CachedTexture existing = textures.get(slot);
            if (existing != null && existing.path().equals(asset) && existing.modified() == modified)
                return existing.id();

            Identifier id = Identifier.of("relian", "cosmetics/" + slot.toLowerCase(java.util.Locale.ROOT));
            if (existing != null) client.getTextureManager().destroyTexture(existing.id());
            NativeImage image;
            try (InputStream input = Files.newInputStream(asset)) {
                image = NativeImage.read(input);
            }
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            textures.put(slot, new CachedTexture(asset, modified, id));
            return id;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Path> readSafely() {
        try {
            if (Files.isSymbolicLink(ROOT) || !Files.isDirectory(ROOT, LinkOption.NOFOLLOW_LINKS))
                return Map.of();
            Path realRoot = ROOT.toRealPath();
            Path manifest = realRoot.resolve("wardrobe.json");
            if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(manifest) > 64 * 1024L) return Map.of();

            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            Map<String, Path> result = new HashMap<>();
            addIfValid(json, realRoot, "CAPE", "capes", result);
            addIfValid(json, realRoot, "WINGS", "wings", result);
            addIfValid(json, realRoot, "HAT", "hats", result);
            return Map.copyOf(result);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void addIfValid(JsonObject json, Path root, String key, String folder,
                                   Map<String, Path> result) throws Exception {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.get(key).getAsJsonPrimitive().isString()) return;
        String relative = json.get(key).getAsString();
        Path expectedFolder = root.resolve(folder).normalize();
        Path asset = root.resolve(relative).normalize();
        if (!asset.startsWith(expectedFolder) || !expectedFolder.startsWith(root)
                || Files.isSymbolicLink(expectedFolder) || Files.isSymbolicLink(asset)
                || !Files.isDirectory(expectedFolder, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(asset, LinkOption.NOFOLLOW_LINKS)) return;
        Path realAsset = asset.toRealPath();
        if (!realAsset.startsWith(expectedFolder.toRealPath()) || Files.size(realAsset) > MAX_ASSET_BYTES)
            return;
        byte[] signature = new byte[8];
        try (InputStream input = Files.newInputStream(realAsset)) {
            if (input.read(signature) != signature.length) return;
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!java.util.Arrays.equals(signature, png)) return;
        result.put(key, realAsset);
    }
}
