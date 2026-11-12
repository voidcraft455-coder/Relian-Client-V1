package com.relian.launcher.settings;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Applies conservative Minecraft-only video defaults; it never changes Windows process priorities or other apps. */
public final class MinecraftPerformanceProfile {
    public enum Preset { BALANCED, FPS_BOOST, ORIGINAL }
    private MinecraftPerformanceProfile() {}

    public static void apply(Path gameDirectory, Preset preset) throws IOException {
        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("The selected Minecraft instance folder does not exist.");
        Path file = root.resolve("options.txt");
        Properties values = new Properties();
        if (Files.isRegularFile(file)) try (var in = Files.newInputStream(file)) { values.load(in); }
        if (preset == Preset.ORIGINAL) return;
        values.setProperty("renderDistance", preset == Preset.FPS_BOOST ? "6" : "8");
        values.setProperty("simulationDistance", preset == Preset.FPS_BOOST ? "4" : "6");
        values.setProperty("entityDistanceScaling", preset == Preset.FPS_BOOST ? "0.5" : "0.75");
        values.setProperty("particles", preset == Preset.FPS_BOOST ? "0" : "1");
        values.setProperty("clouds", "false");
        values.setProperty("biomeBlendRadius", "0");
        values.setProperty("entityShadows", "false");
        values.setProperty("vsync", "false");
        values.setProperty("graphicsMode", "0");
        values.setProperty("maxFps", "260");
        try (var out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            values.store(out, "Relian " + preset + " video profile. Applies only to this Minecraft instance.");
        }
    }
}
