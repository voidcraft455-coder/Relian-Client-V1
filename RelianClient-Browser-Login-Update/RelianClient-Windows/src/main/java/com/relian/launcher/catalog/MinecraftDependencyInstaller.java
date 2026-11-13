package com.relian.launcher.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the version's Mojang-declared libraries, asset index, assets, and Windows natives. */
final class MinecraftDependencyInstaller {
    private static final long MAX_INDEX_BYTES = 32L * 1024 * 1024;
    private static final long MAX_LIBRARY_BYTES = 512L * 1024 * 1024;
    private static final long MAX_ASSET_BYTES = 128L * 1024 * 1024;
    private static final Set<String> HOSTS = Set.of(
            "libraries.minecraft.net", "repo.maven.apache.org", "repo1.maven.org",
            "maven.fabricmc.net", "maven.minecraftforge.net", "resources.download.minecraft.net",
            "piston-meta.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com",
            "piston-data.mojang.com");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();

    record Result(int libraries, int nativeJars, int assets) {}

    private MinecraftDependencyInstaller() {}

    static Result install(JsonObject metadata, Path gameRoot, java.util.function.Consumer<String> progress)
            throws IOException, InterruptedException {
        int libraries = 0, nativeJars = 0, assets = 0;
        Path librariesRoot = gameRoot.resolve("libraries").normalize();
        Path nativesRoot = gameRoot.resolve("versions").resolve(metadata.get("id").getAsString()).resolve("natives").normalize();
        if (hasSymlinkParent(librariesRoot) || hasSymlinkParent(nativesRoot)) {
            throw new IOException("Relian refuses to install Minecraft dependencies through a symbolic link.");
        }
        Files.createDirectories(librariesRoot);
        Files.createDirectories(nativesRoot);

        JsonArray entries = metadata.has("libraries") && metadata.get("libraries").isJsonArray()
                ? metadata.getAsJsonArray("libraries") : new JsonArray();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject library = element.getAsJsonObject();
            if (!allowedByRules(library)) continue;
            JsonObject downloads = object(library, "downloads");
            JsonObject artifact = object(downloads, "artifact");
            if (artifact != null) {
                String artifactPath = string(artifact, "path");
                Path target = safeResolve(librariesRoot, artifactPath);
                downloadArtifact(string(artifact, "url"), string(artifact, "sha1"), target, MAX_LIBRARY_BYTES);
                libraries++;

                // Since Minecraft 1.19, many LWJGL native JARs are declared as
                // ordinary artifacts (their Maven filename carries the native
                // classifier) instead of downloads.classifiers + natives.
                if (isWindows() && isWindowsNativeArtifact(artifactPath, windowsNativeArchitecture())) {
                    extractNativeJar(target, nativesRoot);
                    nativeJars++;
                }
            }
            JsonObject classifiers = object(downloads, "classifiers");
            JsonObject natives = object(library, "natives");
            if (classifiers != null && natives != null && isWindows()) {
                String nativeName = string(natives, "windows");
                String architecture = windowsNativeArchitecture();
                JsonObject nativeArtifact = findWindowsNativeArtifact(classifiers, nativeName, architecture);
                if (nativeArtifact != null) {
                    Path jar = safeResolve(librariesRoot, string(nativeArtifact, "path"));
                    downloadArtifact(string(nativeArtifact, "url"), string(nativeArtifact, "sha1"), jar, MAX_LIBRARY_BYTES);
                    extractNativeJar(jar, nativesRoot);
                    nativeJars++;
                }
            }
        }

        JsonObject logging = object(metadata, "logging");
        JsonObject clientLogging = object(logging, "client");
        JsonObject loggingFile = object(clientLogging, "file");
        if (loggingFile != null) {
            String loggingId = string(loggingFile, "id");
            if (!loggingId.matches("[A-Za-z0-9._-]{1,96}")) throw new IOException("Mojang supplied an invalid logging configuration ID.");
            Path loggingPath = gameRoot.resolve("assets").resolve("log_configs").resolve(loggingId).normalize();
            downloadArtifact(string(loggingFile, "url"), string(loggingFile, "sha1"), loggingPath, 4L * 1024 * 1024);
        }

        JsonObject assetIndex = object(metadata, "assetIndex");
        if (assetIndex != null) {
            String indexId = string(assetIndex, "id");
            if (!indexId.matches("[A-Za-z0-9._-]{1,64}")) throw new IOException("Mojang supplied an invalid asset index ID.");
            String indexUrl = string(assetIndex, "url");
            String indexSha1 = string(assetIndex, "sha1");
            Path indexFile = gameRoot.resolve("assets").resolve("indexes").resolve(indexId + ".json").normalize();
            downloadArtifact(indexUrl, indexSha1, indexFile, MAX_INDEX_BYTES);
            JsonObject index;
            try { index = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject(); }
            catch (RuntimeException ex) { throw new IOException("The Minecraft asset index is invalid.", ex); }
            JsonObject objects = object(index, "objects");
            if (objects != null) {
                Path objectsRoot = gameRoot.resolve("assets").resolve("objects").normalize();
                for (var item : objects.entrySet()) {
                    if (!item.getValue().isJsonObject()) continue;
                    JsonObject object = item.getValue().getAsJsonObject();
                    String hash = string(object, "hash");
                    if (!hash.matches("[a-fA-F0-9]{40}")) throw new IOException("Mojang supplied an invalid asset hash.");
                    Path target = safeResolve(objectsRoot, hash.substring(0, 2) + "/" + hash);
                    if (!Files.isRegularFile(target) || !hash.equalsIgnoreCase(sha1(target))) {
                        URI uri = trustedUri("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash);
                        downloadArtifact(uri.toString(), hash, target, MAX_ASSET_BYTES);
                    }
                    assets++;
                }
            }
        }
        return new Result(libraries, nativeJars, assets);
    }

    private static boolean isWindowsNativeArtifact(String artifactPath, String architecture) {
        if (artifactPath == null || artifactPath.isBlank()) return false;
        String fileName = Path.of(artifactPath).getFileName().toString()
                .toLowerCase(Locale.ROOT);
        String marker = "-natives-windows";
        int markerIndex = fileName.lastIndexOf(marker);
        if (markerIndex < 0 || !fileName.endsWith(".jar")) return false;

        String suffix = fileName.substring(markerIndex + marker.length(),
                fileName.length() - ".jar".length());
        if (suffix.isEmpty()) return true; // Generic Windows native build.
        if (suffix.startsWith("-arm64")) return architecture.equals("arm64");
        if (suffix.startsWith("-x86_64") || suffix.startsWith("-64")) return architecture.equals("64");
        if (suffix.startsWith("-x86") || suffix.startsWith("-32")) return architecture.equals("32");
        return false;
    }

    private static JsonObject findWindowsNativeArtifact(JsonObject classifiers, String nativeName, String architecture) {
        if (nativeName == null || nativeName.isBlank()) return null;
        String expanded = nativeName.replace("${arch}", architecture);
        JsonObject artifact = object(classifiers, expanded);
        if (artifact != null) return artifact;

        // Some Mojang metadata sets publish architecture-suffixed classifier keys
        // even when the platform mapping omits the ${arch} placeholder.
        List<String> candidates = new ArrayList<>();
        if (architecture.equals("arm64")) {
            candidates.add(nativeName + "-arm64");
            candidates.add(nativeName + "-64");
        } else if (architecture.equals("64")) {
            candidates.add(nativeName + "-64");
            candidates.add(nativeName + "-arm64");
        } else {
            candidates.add(nativeName + "-32");
            candidates.add(nativeName + "-x86");
        }
        candidates.add(nativeName);
        for (String candidate : candidates) {
            artifact = object(classifiers, candidate);
            if (artifact != null) return artifact;
        }

        // Be tolerant of classifier keys that use x86_64/arm64 rather than
        // the legacy 64/32 spelling in the platform's natives map.
        String prefix = nativeName.replace("${arch}", "").replaceAll("[-_]$", "");
        JsonObject unqualified = null;
        for (var entry : classifiers.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.equals(prefix.toLowerCase(Locale.ROOT))
                    && !key.startsWith(prefix.toLowerCase(Locale.ROOT) + "-")
                    && !key.startsWith(prefix.toLowerCase(Locale.ROOT) + "_")) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) continue;
            if (key.contains("arm64") && architecture.equals("arm64")) return entry.getValue().getAsJsonObject();
            if ((key.contains("x86_64") || key.endsWith("-64") || key.endsWith("_64"))
                    && architecture.equals("64")) return entry.getValue().getAsJsonObject();
            if ((key.contains("x86") || key.endsWith("-32") || key.endsWith("_32"))
                    && architecture.equals("32")) return entry.getValue().getAsJsonObject();
            if (!key.contains("arm64") && !key.contains("x86") && !key.endsWith("-64")
                    && !key.endsWith("_64") && !key.endsWith("-32") && !key.endsWith("_32")) {
                unqualified = entry.getValue().getAsJsonObject();
            }
        }
        return unqualified;
    }

    private static String windowsNativeArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        return arch.contains("64") ? "64" : "32";
    }

    private static boolean allowedByRules(JsonObject library) {
        JsonArray rules = library.has("rules") && library.get("rules").isJsonArray() ? library.getAsJsonArray("rules") : null;
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            JsonObject os = object(rule, "os");
            boolean matches = os == null || !os.has("name") || "windows".equals(string(os, "name"));
            if (matches && os != null && os.has("arch")) {
                String arch = string(os, "arch");
                matches = arch.isBlank() || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains(arch.toLowerCase(Locale.ROOT));
            }
            if (matches) allowed = "allow".equals(string(rule, "action"));
        }
        return allowed;
    }

    private static void downloadArtifact(String rawUrl, String expectedSha1, Path destination, long limit)
            throws IOException, InterruptedException {
        if (rawUrl.isBlank()) throw new IOException("Minecraft metadata contains an empty library URL.");
        URI uri = trustedUri(rawUrl);
        if (!expectedSha1.matches("(?i)[a-f0-9]{40}")) throw new IOException("Minecraft metadata contains a missing or invalid SHA-1 checksum.");
        if (Files.isSymbolicLink(destination) || hasSymlinkParent(destination.getParent())) {
            throw new IOException("Relian refuses to install Minecraft files through a symbolic link.");
        }
        Files.createDirectories(destination.getParent());
        if (hasSymlinkParent(destination.getParent())) {
            throw new IOException("Relian refuses to install Minecraft files through a symbolic link.");
        }
        if (Files.isRegularFile(destination) && expectedSha1.equalsIgnoreCase(sha1(destination))) return;

        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "RelianClient/0.1.0 (Minecraft installer)").GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Minecraft dependency download failed with HTTP " + response.statusCode() + ".");
        }
        validate(response.uri());
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > limit) {
            response.body().close();
            throw new IOException("A Minecraft dependency exceeds Relian's download size limit.");
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".relian-download-", ".part");
        boolean moved = false;
        try {
            MessageDigest digest = sha1Digest();
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) throw new IOException("A Minecraft dependency exceeded Relian's download size limit.");
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedSha1)) {
                throw new IOException("A Minecraft dependency failed SHA-1 verification.");
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void extractNativeJar(Path jar, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                Path output = safeResolve(destination, entry.getName());
                if (Files.isSymbolicLink(output) || hasSymlinkParent(output.getParent())) {
                    throw new IOException("A native archive attempted to write through a symbolic link.");
                }
                Files.createDirectories(output.getParent());
                if (Files.isSymbolicLink(output) || hasSymlinkParent(output.getParent())) {
                    throw new IOException("A native archive attempted to write through a symbolic link.");
                }
                try (OutputStream out = Files.newOutputStream(output)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) out.write(buffer, 0, count);
                }
            }
        }
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank()) throw new IOException("Minecraft metadata contains an empty file path.");
        Path resolved = root.resolve(relative.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root.normalize())) throw new IOException("Minecraft metadata contains an unsafe file path.");
        return resolved;
    }

    private static boolean hasSymlinkParent(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true;
            current = current.getParent();
        }
        return false;
    }

    private static URI trustedUri(String raw) throws IOException {
        try {
            URI uri = URI.create(raw);
            validate(uri);
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Minecraft metadata contains an invalid dependency URL.", ex);
        }
    }

    private static void validate(URI uri) throws IOException {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !HOSTS.contains(host.toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null) {
            throw new IOException("A Minecraft dependency URL used an unapproved host or protocol.");
        }
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }
    private static String string(JsonObject parent, String key) {
        return parent != null && parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsString() : "";
    }
    private static MessageDigest sha1Digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-1 is unavailable.", ex); }
    }
    private static String sha1(Path file) throws IOException {
        MessageDigest digest = sha1Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
