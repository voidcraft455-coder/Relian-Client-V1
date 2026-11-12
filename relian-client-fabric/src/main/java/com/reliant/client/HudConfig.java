package com.reliant.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("relian-hud.json");
    private static Config current = defaults();

    private HudConfig() {}

    public static Config get() { return current; }

    public static void load() {
        if (!Files.isRegularFile(FILE)) {
            current = defaults();
            save();
            return;
        }
        try {
            Config loaded = GSON.fromJson(Files.readString(FILE), Config.class);
            current = loaded == null || loaded.modules == null ? defaults() : loaded;
            for (String id : defaults().modules.keySet()) {
                current.modules.putIfAbsent(id, defaults().modules.get(id));
            }
        } catch (Exception ignored) {
            current = defaults();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(current));
        } catch (IOException exception) {
            System.err.println("[Relian] Could not save HUD settings: " + exception.getMessage());
        }
    }

    public static void reset() {
        current = defaults();
        save();
    }

    private static Config defaults() {
        Config config = new Config();
        config.modules.put("fps", new ModuleSettings(true, 8, 8, 1.0f, 0xFFFFFFFF));
        config.modules.put("coordinates", new ModuleSettings(true, 8, 30, 1.0f, 0xFF83B7FF));
        config.modules.put("keystrokes", new ModuleSettings(true, 8, 54, 1.0f, 0xFFFFFFFF));
        config.modules.put("armor", new ModuleSettings(true, 8, 112, 1.0f, 0xFFFFFFFF));
        config.modules.put("ping", new ModuleSettings(true, 8, 150, 1.0f, 0xFF8FE3B0));
        config.modules.put("held_item", new ModuleSettings(true, 8, 172, 1.0f, 0xFFFFD479));
        return config;
    }

    public static final class Config {
        public Map<String, ModuleSettings> modules = new LinkedHashMap<>();
    }

    public static final class ModuleSettings {
        public boolean enabled;
        public int x;
        public int y;
        public float scale;
        public int color;

        public ModuleSettings() {}
        public ModuleSettings(boolean enabled, int x, int y, float scale, int color) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.color = color;
        }
    }
}
