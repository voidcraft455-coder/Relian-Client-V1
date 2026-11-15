package com.relian.launcher.marketplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Persistent provider/version/hash ownership records for marketplace-installed files. */
public final class MarketplaceInstallRegistry {
    public record InstallRecord(String source, String projectId, String versionId, String fileName,
                                String relativePath, String hashAlgorithm, String hash,
                                String projectType, String gameVersion, String loader, long installedAt) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = ".relian";
    private static final String FILE = "marketplace-installs.json";
    private MarketplaceInstallRegistry() {}

    public static synchronized void record(Path gameRoot, Path installedFile, String source, String projectId,
            String versionId, String hashAlgorithm, String hash, ProjectType type, String gameVersion, String loader)
            throws IOException {
        Path root = checkedRoot(gameRoot);
        Path file = checkedManagedFile(root, installedFile);
        if (blank(source) || blank(projectId) || blank(versionId) || blank(hash) || type == null)
            throw new IOException("Marketplace install metadata is incomplete; the file remains installed but cannot be tracked for updates.");
        Path registry = registryPath(root);
        JsonArray entries = readEntries(registry);
        String relative = root.relativize(file).toString().replace('\\', '/');
        JsonArray next = new JsonArray();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!relative.equals(string(item, "relativePath"))) next.add(item);
        }
        InstallRecord record = new InstallRecord(source, projectId, versionId, file.getFileName().toString(),
                relative, hashAlgorithm, hash, type.name(), gameVersion == null ? "" : gameVersion,
                loader == null ? "" : loader, Instant.now().toEpochMilli());
        next.add(GSON.toJsonTree(record));
        writeEntries(registry, next);
    }

    public static synchronized List<InstallRecord> list(Path gameRoot) throws IOException {
        Path root = checkedRoot(gameRoot);
        JsonArray entries = readEntries(registryPath(root));
        List<InstallRecord> records = new ArrayList<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            try {
                InstallRecord record = GSON.fromJson(element, InstallRecord.class);
                if (record != null && safeRelative(record.relativePath())) records.add(record);
            } catch (RuntimeException ignored) { /* Ignore malformed records without trusting their paths. */ }
        }
        return List.copyOf(records);
    }

    public static synchronized boolean isProjectInstalled(Path gameRoot, String projectId) throws IOException {
        if (blank(projectId)) return false;
        Path root = checkedRoot(gameRoot);
        for (InstallRecord record : list(root)) {
            if (!projectId.equals(record.projectId())) continue;
            Path file = root.resolve(record.relativePath()).normalize();
            if (file.startsWith(root) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(file)) return true;
        }
        return false;
    }

    public static synchronized void forget(Path gameRoot, Path installedFile) throws IOException {
        Path root = checkedRoot(gameRoot);
        Path file = checkedManagedFile(root, installedFile);
        String relative = root.relativize(file).toString().replace('\\', '/');
        JsonArray entries = readEntries(registryPath(root));
        JsonArray next = new JsonArray();
        for (JsonElement element : entries) {
            if (element.isJsonObject() && !relative.equals(string(element.getAsJsonObject(), "relativePath"))) next.add(element);
        }
        writeEntries(registryPath(root), next);
    }

    private static Path registryPath(Path root) throws IOException {
        Path directory = root.resolve(DIRECTORY);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory))
            throw new IOException("Relian marketplace metadata directory cannot be a symbolic link.");
        Files.createDirectories(directory);
        return directory.resolve(FILE);
    }

    private static JsonArray readEntries(Path registry) throws IOException {
        if (!Files.exists(registry, LinkOption.NOFOLLOW_LINKS)) return new JsonArray();
        if (Files.isSymbolicLink(registry) || !Files.isRegularFile(registry, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Relian marketplace metadata file is not a regular file.");
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(registry));
            if (!parsed.isJsonArray()) throw new IOException("Marketplace metadata has an invalid format.");
            return parsed.getAsJsonArray();
        } catch (RuntimeException ex) {
            throw new IOException("Could not read marketplace install metadata.", ex);
        }
    }

    private static void writeEntries(Path registry, JsonArray entries) throws IOException {
        Path temporary = Files.createTempFile(registry.getParent(), ".marketplace-", ".part");
        try {
            Files.writeString(temporary, GSON.toJson(entries));
            try { Files.move(temporary, registry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, registry, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Path checkedManagedFile(Path root, Path candidate) throws IOException {
        if (candidate == null) throw new IOException("Marketplace file path is missing.");
        Path file = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
            throw new IOException("Marketplace file must be a regular, non-symbolic-link file.");
        Path real = file.toRealPath();
        Path parent = real.getParent();
        if (parent == null || !root.equals(parent.getParent())) throw new IOException("Marketplace file is outside a supported game content folder.");
        String folder = parent.getFileName().toString();
        String name = real.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean supported = switch (folder) {
            case "mods" -> name.endsWith(".jar") || name.endsWith(".jar.disabled");
            case "resourcepacks", "shaderpacks" -> name.endsWith(".zip") || name.endsWith(".zip.disabled");
            default -> false;
        };
        if (!supported) throw new IOException("Marketplace file is outside a supported content folder.");
        return real;
    }

    private static boolean safeRelative(String value) {
        if (blank(value) || value.startsWith("/") || value.contains("\\\\")) return false;
        Path path = Path.of(value).normalize();
        return !path.isAbsolute() && !path.startsWith("..") && path.getNameCount() == 2
                && List.of("mods", "resourcepacks", "shaderpacks").contains(path.getName(0).toString());
    }
    private static String string(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Path checkedRoot(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Choose an existing, non-symbolic-link game directory.");
        return root.toRealPath();
    }
}
