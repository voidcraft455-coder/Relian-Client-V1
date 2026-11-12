package com.relian.launcher.settings;

import java.io.IOException;
import java.nio.file.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Applies conservative Minecraft-only video defaults; it never changes Windows process priorities or other apps. */
public final class MinecraftPerformanceProfile {
    public enum Preset { BALANCED, FPS_BOOST, ORIGINAL }
    private MinecraftPerformanceProfile() {}

    public static void apply(Path gameDirectory, Preset preset) throws IOException {
        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("The selected Minecraft instance folder does not exist.");
        Path file = root.resolve("options.txt");
        java.util.Map<String,String> values = new java.util.LinkedHashMap<>();
        if (Files.isRegularFile(file)) for (String line : Files.readAllLines(file)) {
            int split = line.indexOf(':');
            if (split > 0) values.put(line.substring(0, split), line.substring(split + 1));
        }
        if (preset == Preset.ORIGINAL) return;
        String version = "";
        Path manifest = root.resolve("relian-instance.json");
        if (Files.isRegularFile(manifest)) try {
            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            if (json.has("minecraftVersion")) version = json.get("minecraftVersion").getAsString();
        } catch (RuntimeException ignored) {}
        boolean legacy = version.startsWith("1.8.");
        values.put("renderDistance", preset == Preset.FPS_BOOST ? "6" : "8");
        values.put("particles", preset == Preset.FPS_BOOST ? "0" : "1");
        values.put("entityShadows", "false");
        values.put("vsync", "false");
        values.put("maxFps", "260");
        if (legacy) {
            values.put("clouds", "false");
            values.put("fancyGraphics", "false");
            values.put("ao", "0");
        } else {
            values.put("simulationDistance", preset == Preset.FPS_BOOST ? "4" : "6");
            values.put("entityDistanceScaling", preset == Preset.FPS_BOOST ? "0.5" : "0.75");
            values.put("clouds", "0");
            values.put("biomeBlendRadius", "0");
            values.put("graphicsMode", "0");
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        values.forEach((key,value) -> lines.add(key + ":" + value));
        Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
