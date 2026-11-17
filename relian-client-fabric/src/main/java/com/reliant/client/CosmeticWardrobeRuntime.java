package com.reliant.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the instance-scoped wardrobe bundle exported by the Relian launcher. */
final class CosmeticWardrobeRuntime {
    private static final long MAX_ASSET_BYTES = 8L * 1024L * 1024L;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("relian").resolve("cosmetics").toAbsolutePath().normalize();
    private static volatile long lastRead;
    private static volatile List<String> cached = List.of();

    private CosmeticWardrobeRuntime() {}

    static List<String> lines() {
        long now = System.currentTimeMillis();
        if (now - lastRead < 1500L) return cached;
        synchronized (CosmeticWardrobeRuntime.class) {
            if (now - lastRead < 1500L) return cached;
            cached = readSafely();
            lastRead = now;
            return cached;
        }
    }

    private static List<String> readSafely() {
        try {
            if (Files.isSymbolicLink(ROOT) || !Files.isDirectory(ROOT, LinkOption.NOFOLLOW_LINKS))
                return List.of();
            Path realRoot = ROOT.toRealPath();
            Path manifest = realRoot.resolve("wardrobe.json");
            if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(manifest) > 64 * 1024L) return List.of();

            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            List<String> result = new ArrayList<>();
            addIfValid(json, realRoot, "CAPE", "capes", result);
            addIfValid(json, realRoot, "WINGS", "wings", result);
            addIfValid(json, realRoot, "HAT", "hats", result);
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void addIfValid(JsonObject json, Path root, String key, String folder,
                                   List<String> result) throws IOException {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return;
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
        try (var input = Files.newInputStream(realAsset)) {
            if (input.read(signature) != signature.length) return;
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!java.util.Arrays.equals(signature, png)) return;
        result.add(key + ": " + asset.getFileName());
    }
}
