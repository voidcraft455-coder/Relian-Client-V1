package com.relian.launcher.profiles;

import java.nio.file.Path;
import java.util.Objects;

/** User installation profile. Runtime and loader details are resolved from version metadata at install time. */
public record MinecraftProfile(
        String name,
        String minecraftVersion,
        Loader loader,
        String loaderVersion,
        Path gameDirectory,
        int maxMemoryMb) {

    public enum Loader { VANILLA, FABRIC, FORGE, NEOFORGE, QUILT }

    public MinecraftProfile {
        name = Objects.requireNonNull(name, "name").trim();
        minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion").trim();
        loader = Objects.requireNonNull(loader, "loader");
        gameDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory").toAbsolutePath().normalize();
        if (name.isEmpty()) throw new IllegalArgumentException("Profile name cannot be empty.");
        if (minecraftVersion.isEmpty()) throw new IllegalArgumentException("Minecraft version cannot be empty.");
        if (maxMemoryMb < 512) throw new IllegalArgumentException("At least 512 MB of memory must be allocated.");
        if (loader == Loader.VANILLA) loaderVersion = "";
        else if (loaderVersion == null || loaderVersion.isBlank()) throw new IllegalArgumentException("A loader version is required for modded profiles.");
    }
}