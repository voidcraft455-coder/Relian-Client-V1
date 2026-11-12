package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports Modrinth formatVersion 1 packs into an already-created, isolated Relian instance.
 * The importer deliberately handles client files only and never copies launcher credentials.
 */
public final class ModrinthPackImporter {
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 300L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_OVERRIDE_TOTAL_BYTES = 128L * 1024 * 1024;
    private static final long MAX_OVERRIDE_FILE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final double MAX_COMPRESSION_RATIO = 150.0;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record ImportResult(String packName, String packVersion, String minecraftVersion,
                               String loader, int installedFiles, int skippedFiles) {}
    private record PlannedFile(String path, URI url, Map<String, String> hashes, long expectedSize) {}

    private ModrinthPackImporter() {}

    public static ImportResult importPack(Path archive, Path instanceDirectory,
                                          String selectedMinecraftVersion, String selectedLoader)
            throws IOException, InterruptedException {
        if (archive == null || instanceDirectory == null) throw new IOException("Choose a modpack archive and an instance.");
        Path zipPath = archive.toAbsolutePath().normalize();
        Path instance = instanceDirectory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(zipPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(zipPath))
            throw new IOException("The selected .mrpack file is not a regular file.");
        long archiveSize = Files.size(zipPath);
        if (archiveSize <= 0 || archiveSize > MAX_ARCHIVE_BYTES)
            throw new IOException("The .mrpack archive is empty or exceeds the 1 GB safety limit.");
        if (!Files.isDirectory(instance, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(instance))
            throw new IOException("The selected Relian instance folder is not safe.");
        if (!Files.isRegularFile(instance.resolve("relian-instance.json"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Create a Relian instance before importing a modpack.");
        if ("VANILLA".equalsIgnoreCase(instanceKind(instance)))
            throw new IOException("Modrinth modpacks cannot be imported into a Vanilla instance. Choose Modpack, Relian, or Import.");

        Path stage = Files.createTempDirectory(instance, ".relian-mrpack-stage-");
        List<Path> installed = new ArrayList<>();
        List<Path> createdDirectories = new ArrayList<>();
        boolean complete = false;
        try {
            JsonObject index;
            List<PlannedFile> downloads = new ArrayList<>();
            Map<String, byte[]> overrides = new LinkedHashMap<>();
            try (ZipFile zip = new ZipFile(zipPath.toFile(), "UTF-8", true)) {
                index = readAndValidateArchive(zip, downloads, overrides);
            }
            String packName = requiredString(index, "name", "The modpack manifest is missing its name.");
            String packVersion = optionalString(index, "versionId");
            JsonObject dependencies = index.has("dependencies") && index.get("dependencies").isJsonObject()
                    ? index.getAsJsonObject("dependencies") : new JsonObject();
            String packMinecraft = requiredString(dependencies, "minecraft",
                    "The modpack manifest does not declare a Minecraft version.");
            if (!packMinecraft.equals(selectedMinecraftVersion))
                throw new IOException("This pack targets Minecraft " + packMinecraft
                        + ", but the instance is set to " + selectedMinecraftVersion + ". Choose the matching version.");
            String loader = validateLoader(dependencies, selectedLoader);

            Set<String> plannedPaths = new HashSet<>();
            long plannedTotal = 0;
            for (PlannedFile file : downloads) {
                String key = collisionKey(file.path());
                if (!plannedPaths.add(key)) throw new IOException("The modpack contains duplicate destination paths: " + file.path());
                if (file.expectedSize() >= 0) {
                    plannedTotal = Math.addExact(plannedTotal, file.expectedSize());
                    if (plannedTotal > MAX_TOTAL_BYTES) throw new IOException("The modpack's declared downloads exceed the 1 GB safety limit.");
                }
            }
            for (String overridePath : overrides.keySet()) {
                String key = collisionKey(overridePath);
                if (!plannedPaths.add(key)) throw new IOException("A modpack override conflicts with a downloaded file: " + overridePath);
            }

            long downloadedTotal = 0;
            for (int i = 0; i < downloads.size(); i++) {
                PlannedFile file = downloads.get(i);
                Path staged = stage.resolve("download-" + i + ".part");
                downloadedTotal = Math.addExact(downloadedTotal, downloadAndVerify(file, staged));
                if (downloadedTotal > MAX_TOTAL_BYTES)
                    throw new IOException("The modpack's actual downloads exceed the 1 GB safety limit.");
                writeStagedPath(stage, file.path(), staged);
            }
            for (Map.Entry<String, byte[]> override : overrides.entrySet()) {
                Path staged = safeResolve(stage.resolve("overrides"), override.getKey());
                Files.createDirectories(staged.getParent());
                Files.write(staged, override.getValue());
            }

            List<Path> stagedFiles;
            try (var walk = Files.walk(stage)) {
                stagedFiles = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> !p.getFileName().toString().endsWith(".part"))
                        .toList();
            }
            for (Path stagedFile : stagedFiles) {
                Path relative = stage.relativize(stagedFile);
                Path targetRelative;
                if (relative.getNameCount() > 0 && "overrides".equals(relative.getName(0).toString()))
                    targetRelative = relative.subpath(1, relative.getNameCount());
                else {
                    // Download staging paths are mirrored below stage/payload.
                    if (relative.getNameCount() < 2 || !"payload".equals(relative.getName(0).toString())) continue;
                    targetRelative = relative.subpath(1, relative.getNameCount());
                }
                Path destination = safeResolve(instance, targetRelative.toString().replace('\\\\', '/'));
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Import would overwrite an existing instance file: " + targetRelative);
                createDirectoriesTracked(instance, destination.getParent(), createdDirectories);
                moveNew(stagedFile, destination);
                installed.add(destination);
            }

            JsonObject imported = new JsonObject();
            imported.addProperty("managedBy", "Relian Client");
            imported.addProperty("formatVersion", 1);
            imported.addProperty("name", packName);
            imported.addProperty("versionId", packVersion);
            imported.addProperty("minecraftVersion", packMinecraft);
            imported.addProperty("loader", loader);
            imported.addProperty("installedFiles", installed.size());
            Files.writeString(instance.resolve("relian-modpack.json"), imported.toString(),
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            installed.add(instance.resolve("relian-modpack.json"));
            complete = true;
            return new ImportResult(packName, packVersion, packMinecraft, loader, installed.size() - 1, 0);
        } catch (ArithmeticException ex) {
            throw new IOException("The modpack declares an invalid or excessive combined file size.", ex);
        } catch (RuntimeException ex) {
            throw new IOException("The Modrinth modpack manifest or archive metadata is malformed.", ex);
        } finally {
            deleteTree(stage);
            if (!complete) {
                for (int i = installed.size() - 1; i >= 0; i--) {
                    try { Files.deleteIfExists(installed.get(i)); } catch (IOException ignored) {}
                }
                createdDirectories.sort(java.util.Comparator.comparingInt(Path::getNameCount).reversed());
                for (Path directory : createdDirectories) {
                    try { Files.deleteIfExists(directory); } catch (IOException ignored) {}
                }
            }
        }
    }

    private static JsonObject readAndValidateArchive(ZipFile zip, List<PlannedFile> downloads,
                                                       Map<String, byte[]> overrides) throws IOException {
        JsonObject index = null;
        Set<String> archiveNames = new HashSet<>();
        long declaredExpanded = 0;
        long overrideBytes = 0;
        int count = 0;
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (++count > MAX_ENTRIES) throw new IOException("The archive contains too many entries.");
            if (entry.isUnixSymlink()) throw new IOException("The archive contains a symbolic link, which Relian will not import.");
            String name = entry.getName();
            validateArchiveName(name, entry.isDirectory());
            String key = name.toLowerCase(Locale.ROOT);
            if (!archiveNames.add(key)) throw new IOException("The archive contains duplicate paths: " + name);
            long size = entry.getSize();
            if (size > MAX_FILE_BYTES) throw new IOException("An archive entry exceeds the 300 MB safety limit: " + name);
            if (size > 0) {
                declaredExpanded = Math.addExact(declaredExpanded, size);
                if (declaredExpanded > MAX_TOTAL_BYTES) throw new IOException("The expanded archive exceeds the 1 GB safety limit.");
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && (double) size / compressed > MAX_COMPRESSION_RATIO)
                    throw new IOException("An archive entry has an unsafe compression ratio: " + name);
            }
            if (entry.isDirectory()) continue;
            if ("modrinth.index.json".equals(name)) {
                if (index != null) throw new IOException("The archive contains more than one modrinth.index.json.");
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] bytes = readLimited(in, 2L * 1024 * 1024);
                    index = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (RuntimeException ex) {
                    throw new IOException("The modrinth.index.json manifest is invalid.", ex);
                }
            } else if (name.startsWith("overrides/")) {
                String relative = name.substring("overrides/".length());
                if (relative.isBlank()) continue;
                validateRelativePath(relative);
                if (isProtectedPath(relative)) throw new IOException("The archive attempts to install a protected launcher or credential file.");
                if (size > 32L * 1024 * 1024) throw new IOException("An override file exceeds the 32 MB safety limit: " + relative);
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] content = readLimited(in, 32L * 1024 * 1024);
                    overrideBytes = Math.addExact(overrideBytes, content.length);
                    if (overrideBytes > 128L * 1024 * 1024)
                        throw new IOException("Modpack overrides exceed the 128 MB safety limit.");
                    overrides.put(relative, content);
                }
            } else if (!name.startsWith("server-overrides/")) {
                throw new IOException("The archive contains an unsupported top-level entry: " + name);
            }
        }
        if (index == null) throw new IOException("This file is not a valid Modrinth .mrpack: modrinth.index.json is missing.");
        if (intValue(index, "formatVersion", -1) != 1) throw new IOException("Only Modrinth modpack format version 1 is supported.");
        if (!"minecraft".equals(optionalString(index, "game"))) throw new IOException("This Modrinth pack does not target Minecraft.");
        JsonArray files = index.has("files") && index.get("files").isJsonArray() ? index.getAsJsonArray("files") : new JsonArray();
        for (JsonElement element : files) {
            if (!element.isJsonObject()) throw new IOException("The modpack manifest contains an invalid file entry.");
            JsonObject file = element.getAsJsonObject();
            JsonObject env = file.has("env") && file.get("env").isJsonObject() ? file.getAsJsonObject("env") : new JsonObject();
            String client = optionalString(env, "client");
            String server = optionalString(env, "server");
            if ("unsupported".equals(client)) continue;
            String path = requiredString(file, "path", "A modpack file entry is missing its destination path.");
            validateRelativePath(path);
            if (isProtectedPath(path)) throw new IOException("The modpack attempts to install a protected launcher or credential file: " + path);
            JsonArray urls = file.has("downloads") && file.get("downloads").isJsonArray() ? file.getAsJsonArray("downloads") : new JsonArray();
            if (urls.isEmpty()) throw new IOException("A required client file has no download URL: " + path);
            URI url = trustedDownloadUri(urls.get(0).getAsString());
            JsonObject hashObject = file.has("hashes") && file.get("hashes").isJsonObject() ? file.getAsJsonObject("hashes") : new JsonObject();
            Map<String, String> hashes = new LinkedHashMap<>();
            for (String algorithm : List.of("sha512", "sha1")) {
                String value = optionalString(hashObject, algorithm);
                if (!value.isBlank()) hashes.put(algorithm, value.toLowerCase(Locale.ROOT));
            }
            long fileSize = longValue(file, "fileSize", -1);
            if (fileSize < -1 || fileSize > MAX_FILE_BYTES) throw new IOException("A modpack file has an invalid or excessive declared size: " + path);
            downloads.add(new PlannedFile(path, url, Map.copyOf(hashes), fileSize));
        }
        return index;
    }

    private static long downloadAndVerify(PlannedFile file, Path target) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = sendTrustedDownload(file.url());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("A modpack file download failed with HTTP " + response.statusCode() + ": " + file.path());
        }
        URI finalUri = response.uri();
        if (!isTrustedHost(finalUri)) {
            response.body().close();
            throw new IOException("A modpack download redirected outside the trusted Modrinth CDN.");
        }
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declaredLength > MAX_FILE_BYTES) {
            response.body().close();
            throw new IOException("A modpack download exceeds the 300 MB safety limit.");
        }
        Map<String, MessageDigest> digests = new LinkedHashMap<>();
        try {
            for (String algorithm : file.hashes().keySet()) digests.put(algorithm, MessageDigest.getInstance(algorithm.replace("sha", "SHA-")));
        } catch (NoSuchAlgorithmException ex) {
            response.body().close();
            throw new IOException("A required checksum algorithm is unavailable.", ex);
        }
        long total = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) throw new IOException("A modpack download exceeded the 300 MB safety limit.");
                for (MessageDigest digest : digests.values()) digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        if (file.expectedSize() >= 0 && total != file.expectedSize())
            throw new IOException("Downloaded file size does not match the manifest: " + file.path());
        for (Map.Entry<String, MessageDigest> digest : digests.entrySet()) {
            String actual = HexFormat.of().formatHex(digest.getValue().digest());
            if (!actual.equalsIgnoreCase(file.hashes().get(digest.getKey())))
                throw new IOException("Checksum verification failed for modpack file: " + file.path());
        }
        return total;
    }

    private static void writeStagedPath(Path stage, String relative, Path downloaded) throws IOException {
        Path target = safeResolve(stage.resolve("payload"), relative);
        Files.createDirectories(target.getParent());
        Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static HttpResponse<InputStream> sendTrustedDownload(URI initial)
            throws IOException, InterruptedException {
        URI current = initial;
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!isTrustedHost(current)) throw new IOException("A modpack download redirected outside the trusted Modrinth CDN.");
            HttpRequest request = HttpRequest.newBuilder(current).timeout(Duration.ofMinutes(4))
                    .header("User-Agent", "RelianClient/0.1.0 (Modrinth modpack importer)").GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse("");
                response.body().close();
                if (location.isBlank() || redirects == 3)
                    throw new IOException("The Modrinth CDN returned an invalid or excessive redirect chain.");
                try { current = current.resolve(URI.create(location)); }
                catch (IllegalArgumentException ex) { throw new IOException("The Modrinth CDN returned an invalid redirect URL.", ex); }
                continue;
            }
            return response;
        }
        throw new IOException("The Modrinth CDN returned too many redirects.");
    }

    private static URI trustedDownloadUri(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !isTrustedHost(uri))
                throw new IOException("Modpack files must use HTTPS links on cdn.modrinth.com.");
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IOException("The modpack contains an invalid download URL.", ex);
        }
    }

    private static boolean isTrustedHost(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "cdn.modrinth.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static String validateLoader(JsonObject dependencies, String selectedLoader) throws IOException {
        String chosen = selectedLoader == null ? "" : selectedLoader.trim().toLowerCase(Locale.ROOT);
        String key = "";
        for (String candidate : List.of("fabric-loader", "forge", "neoforge", "quilt-loader")) {
            if (dependencies.has(candidate) && !optionalString(dependencies, candidate).isBlank()) {
                if (!key.isBlank()) throw new IOException("The modpack declares more than one mod loader.");
                key = candidate;
            }
        }
        String declared = switch (key) {
            case "fabric-loader" -> "fabric";
            case "forge" -> "forge";
            case "neoforge" -> "neoforge";
            case "quilt-loader" -> "quilt";
            default -> "vanilla";
        };
        if (!declared.equals(chosen))
            throw new IOException("This modpack requires the " + declared + " loader, but the instance is set to " + chosen + ".");
        return declared;
    }

    private static String instanceKind(Path instance) throws IOException {
        try {
            JsonObject manifest = JsonParser.parseString(Files.readString(instance.resolve("relian-instance.json"))).getAsJsonObject();
            return optionalString(manifest, "kind");
        } catch (RuntimeException ex) {
            throw new IOException("The Relian instance manifest is invalid.", ex);
        }
    }

    private static void validateArchiveName(String name, boolean directory) throws IOException {
        if (name == null || name.isBlank() || name.indexOf('\\\\') >= 0 || name.startsWith("/") || name.startsWith("\\\\"))
            throw new IOException("The archive contains an unsafe entry name.");
        String normalized = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        validateRelativePath(normalized);
    }

    private static void validateRelativePath(String value) throws IOException {
        if (value == null || value.isBlank() || value.indexOf('\\\\') >= 0 || value.startsWith("/")
                || value.matches("^[A-Za-z]:.*") || value.indexOf('\0') >= 0)
            throw new IOException("The modpack contains an unsafe destination path.");
        String[] parts = value.split("/", -1);
        for (String part : parts) {
            String device = part.toUpperCase(Locale.ROOT);
            int dot = device.indexOf('.');
            if (dot >= 0) device = device.substring(0, dot);
            boolean invalidCharacter = part.chars().anyMatch(ch -> ch < 32 || "<>|?*".indexOf(ch) >= 0);
            if (part.isBlank() || ".".equals(part) || "..".equals(part) || part.contains(":")
                    || invalidCharacter || part.endsWith(".") || part.endsWith(" ")
                    || Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                              "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9").contains(device))
                throw new IOException("The modpack contains a path traversal or invalid Windows path segment.");
        }
    }

    private static boolean isProtectedPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String[] segments = lower.split("/");
        String leaf = segments[segments.length - 1];
        if (Set.of("launcher_profiles.json", "launcher_accounts.json", "accounts.json",
                "credentials.json", "usercache.json", "launcher_msa_credentials.json",
                "relian-instance.json", "relian-modules.json", "relian-modpack.json").contains(leaf)
                || leaf.endsWith(".token") || leaf.endsWith(".credentials")) return true;
        for (String segment : segments) {
            if (Set.of("launcher", "auth", "msa", ".relian").contains(segment)) return true;
        }
        return false;
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        validateRelativePath(relative);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!result.startsWith(normalizedRoot)) throw new IOException("The modpack destination escapes the instance folder.");
        return result;
    }

    private static String collisionKey(String path) {
        return path.replace('\\\\', '/').toLowerCase(Locale.ROOT);
    }

    private static void createDirectoriesTracked(Path root, Path directory, List<Path> created) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IOException("The modpack destination escapes the instance folder.");
        List<Path> missing = new ArrayList<>();
        Path current = normalized;
        while (current != null && current.startsWith(root) && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(current);
            current = current.getParent();
        }
        if (current == null || !current.startsWith(root) || Files.isSymbolicLink(current))
            throw new IOException("The modpack destination contains an unsafe directory.");
        Files.createDirectories(normalized);
        created.addAll(missing);
    }

    private static void moveNew(Path source, Path destination) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refusing to overwrite an existing file: " + destination);
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination);
        }
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        try (InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("An archive entry exceeds its allowed size.");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static String requiredString(JsonObject object, String key, String message) throws IOException {
        String value = optionalString(object, key);
        if (value.isBlank()) throw new IOException(message);
        return value;
    }
    private static String optionalString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static int intValue(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
    private static long longValue(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
}
