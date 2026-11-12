package com.relian.launcher.profiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Creates isolated game directories and imports safe, user-selected game content. */
public final class InstanceManager {
    public enum Kind { VANILLA, IMPORT, RELIAN, MODPACK }
    public record InstanceManifest(String name, String minecraftVersion, String loader, Kind kind,
                                   String importedFrom, String modpackTitle, List<String> relianModules) {}
    public record CreatedInstance(Path directory, InstanceManifest manifest) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> RELIAN_MODULES = List.of(
        "sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling", "dynamic-fps"
    );

    private InstanceManager() {}

    public static CreatedInstance create(Path instancesRoot, String name, String version, String loader,
                                         Kind kind, Path importSource, String modpackTitle) throws IOException {
        Objects.requireNonNull(instancesRoot); Objects.requireNonNull(kind);
        String cleanName = safeName(name);
        if (version == null || version.isBlank()) throw new IOException("Choose a Minecraft version.");
        Path root = instancesRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(cleanName).normalize();
        if (!target.startsWith(root) || Files.exists(target)) throw new IOException("An instance with this name already exists.");
        Files.createDirectories(target);
        boolean complete = false;
        try {
            for (String folder : List.of("mods","config","resourcepacks","shaderpacks","saves","screenshots"))
                Files.createDirectories(target.resolve(folder));
            String sourceLabel = "";
            if (kind == Kind.IMPORT) {
                if (importSource == null || !Files.isDirectory(importSource, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Choose a valid existing instance folder.");
                Path source = resolveGameDirectory(importSource);
                sourceLabel = source.toString();
                copySupportedContent(source, target);
            }
            writePerformanceDefaults(target);
            List<String> modules = kind == Kind.VANILLA ? List.of() : RELIAN_MODULES;
            InstanceManifest manifest = new InstanceManifest(cleanName, version,
                    kind == Kind.VANILLA ? "Vanilla" : (loader == null ? "Fabric" : loader), kind, sourceLabel,
                    modpackTitle == null ? "" : modpackTitle, modules);
            Files.writeString(target.resolve("relian-instance.json"), GSON.toJson(manifest),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (!modules.isEmpty()) {
                Files.writeString(target.resolve("relian-modules.json"), GSON.toJson(Map.of(
                        "managedBy", "Relian Client",
                        "status", "pending-install",
                        "note", "These Modrinth performance modules must be downloaded for the selected Minecraft version and loader before launch.",
                        "modules", modules)),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            complete = true;
            return new CreatedInstance(target, manifest);
        } finally {
            if (!complete) deleteTree(target);
        }
    }

    public static Path resolveGameDirectory(Path selected) throws IOException {
        Path source = selected.toAbsolutePath().normalize();
        if (Files.isDirectory(source.resolve(".minecraft"), LinkOption.NOFOLLOW_LINKS)) return source.resolve(".minecraft");
        if (Files.isDirectory(source.resolve("minecraft"), LinkOption.NOFOLLOW_LINKS)) return source.resolve("minecraft");
        return source;
    }

    private static void copySupportedContent(Path source, Path target) throws IOException {
        Path normalizedSource = source.toRealPath();
        for (String folder : List.of("mods","config","resourcepacks","shaderpacks","saves","screenshots")) {
            Path from = normalizedSource.resolve(folder);
            if (Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(from))
                copyTree(from, target.resolve(folder));
        }
        for (String file : List.of("options.txt","servers.dat","servers.dat_old","optionsof.txt")) {
            Path from = normalizedSource.resolve(file);
            if (Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(from))
                Files.copy(from, target.resolve(file), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, EnumSet.noneOf(FileVisitOption.class), 24, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                Path relative = source.relativize(dir);
                Files.createDirectories(destination.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                Path relative = source.relativize(file);
                Path out = destination.resolve(relative).normalize();
                if (!out.startsWith(destination)) throw new IOException("Unsafe imported file path.");
                Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writePerformanceDefaults(Path dir) throws IOException {
        Path options = dir.resolve("options.txt");
        java.util.Map<String,String> values = new java.util.LinkedHashMap<>();
        if (Files.isRegularFile(options)) for (String line : Files.readAllLines(options)) {
            int split = line.indexOf(':');
            if (split > 0) values.put(line.substring(0, split), line.substring(split + 1));
        }
        values.put("renderDistance", "8");
        values.put("simulationDistance", "5");
        values.put("entityDistanceScaling", "0.75");
        values.put("particles", "1");
        values.put("clouds", "false");
        values.put("biomeBlendRadius", "0");
        values.put("entityShadows", "false");
        values.put("vsync", "false");
        values.put("maxFps", "260");
        values.put("graphicsMode", "0");
        java.util.List<String> lines = new java.util.ArrayList<>();
        values.forEach((key,value) -> lines.add(key + ":" + value));
        Files.write(options, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String safeName(String name) throws IOException {
        if (name == null) throw new IOException("Enter an instance name.");
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9 _.-]", "").replaceAll("[. ]+$", "");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) throw new IOException("Enter a valid instance name.");
        return cleaned;
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
