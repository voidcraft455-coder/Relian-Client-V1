package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.relian.launcher.profiles.InstanceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Safely imports a CurseForge manifest ZIP using the official API and publisher checksums. */
public final class CurseForgeModpackImporter {
    private static final URI API = URI.create("https://api.curseforge.com/v1");
    private static final long MAX_ARCHIVE = 512L * 1024L * 1024L;
    private static final long MAX_FILE = 300L * 1024L * 1024L;
    private static final long MAX_TOTAL = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 12_000;
    private static final int MAX_MANIFEST = 5 * 1024 * 1024;
    private static final Set<String> FOLDERS = Set.of("mods", "config", "resourcepacks", "shaderpacks",
            "defaultconfigs", "kubejs", "scripts", "datapacks", "global_packs", "patchouli_books",
            "journeymap", "xaero", "schematics", "saves");
    private static final Set<String> FILES = Set.of("options.txt", "optionsof.txt", "optionsshaders.txt");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private CurseForgeModpackImporter() {}

    public record PackInfo(String name, String minecraftVersion, String loader, String loaderVersion,
                           int requiredFiles, int overrideFiles) {}

    public static InstanceManager.CreatedInstance importPack(Path archive, Path instancesRoot,
            String instanceName, String apiKey) throws IOException, InterruptedException {
        if (archive == null || instancesRoot == null) throw new IllegalArgumentException("Choose a CurseForge ZIP and an instances folder.");
        if (apiKey == null || apiKey.isBlank()) throw new IOException("CurseForge modpack import requires RELIAN_CURSEFORGE_API_KEY.");
        Path source = archive.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IOException("Choose a regular CurseForge modpack ZIP file.");
        }
        long archiveSize = Files.size(source);
        if (archiveSize <= 0 || archiveSize > MAX_ARCHIVE) throw new IOException("The modpack ZIP must be between 1 byte and 512 MB.");

        Path root = instancesRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The instances location is not a safe directory.");
        root = root.toRealPath();
        String cleanName = InstanceManager.normalizeInstanceName(instanceName);
        Path finalTarget = root.resolve(cleanName).normalize();
        if (!finalTarget.startsWith(root) || finalTarget.equals(root) || Files.exists(finalTarget, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("An instance with this name already exists or the destination is unsafe.");

        Path stagingRoot = Files.createTempDirectory(root, ".relian-curseforge-" + UUID.randomUUID() + "-");
        boolean published = false;
        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            JsonObject manifest = readManifest(zip);
            PackInfo info = parseManifest(manifest);
            JsonArray files = array(manifest, "files");
            Path stagedInstance = stagingRoot.resolve(cleanName);
            InstanceManager.CreatedInstance created = InstanceManager.create(stagingRoot, cleanName,
                    info.minecraftVersion(), info.loader(), InstanceManager.Kind.MODPACK, null, info.name());

            long[] downloadedBytes = {0};
            int required = 0;
            for (JsonElement element : files) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                if (file.has("required") && !file.get("required").getAsBoolean()) continue;
                int projectId = integer(file, "projectID");
                int fileId = integer(file, "fileID");
                if (projectId <= 0 || fileId <= 0) throw new IOException("The manifest contains an invalid project/file reference.");
                installMod(projectId, fileId, apiKey, stagedInstance.resolve("mods"), downloadedBytes);
                required++;
            }

            int overrideFiles = extractOverrides(zip, stagedInstance);
            writeMetadata(stagedInstance, info, source.getFileName().toString(), required, overrideFiles);
            if (Files.exists(finalTarget, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("An instance with this name was created while import was running.");
            try {
                Files.move(created.directory(), finalTarget, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(created.directory(), finalTarget);
            }
            published = true;
            return new InstanceManager.CreatedInstance(finalTarget, created.manifest());
        } finally {
            if (!published) deleteTree(stagingRoot);
            else deleteTree(stagingRoot);
        }
    }

    private static JsonObject readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("manifest.json");
        if (entry == null || entry.isDirectory() || entry.getSize() < 1 || entry.getSize() > MAX_MANIFEST)
            throw new IOException("The ZIP does not contain a valid root manifest.json file.");
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(MAX_MANIFEST + 1);
            if (bytes.length > MAX_MANIFEST) throw new IOException("The CurseForge manifest exceeds 5 MB.");
            try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException ex) { throw new IOException("The CurseForge manifest is invalid JSON.", ex); }
        }
    }

    private static PackInfo parseManifest(JsonObject manifest) throws IOException {
        if (!"minecraftModpack".equals(string(manifest, "manifestType")))
            throw new IOException("The selected ZIP is not a Minecraft CurseForge modpack manifest.");
        String name = fallback(string(manifest, "name"), "CurseForge Modpack");
        JsonObject minecraft = object(manifest, "minecraft");
        String version = string(minecraft, "version");
        if (version.isBlank()) throw new IOException("The modpack manifest does not specify a Minecraft version.");
        JsonArray loaders = array(minecraft, "modLoaders");
        String loader = "Vanilla", loaderVersion = "";
        for (JsonElement element : loaders) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (item.has("primary") && !item.get("primary").getAsBoolean()) continue;
            String id = string(item, "id");
            int split = id.indexOf('-');
            if (split <= 0 || split == id.length() - 1) continue;
            String candidate = id.substring(0, split).toLowerCase(Locale.ROOT);
            loader = switch (candidate) {
                case "fabric" -> "Fabric";
                case "forge" -> "Forge";
                case "neoforge" -> "NeoForge";
                case "quilt" -> "Quilt";
                default -> "Vanilla";
            };
            loaderVersion = id.substring(split + 1);
            break;
        }
        JsonArray files = array(manifest, "files");
        return new PackInfo(name, version, loader, loaderVersion, files.size(), 0);
    }

    private static void installMod(int projectId, int fileId, String apiKey, Path modsDirectory,
            long[] totalBytes) throws IOException, InterruptedException {
        URI metadataUri = URI.create(API + "/mods/" + projectId + "/files/" + fileId);
        HttpRequest metadataRequest = HttpRequest.newBuilder(metadataUri).timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey.trim()).header("Accept", "application/json")
                .header("User-Agent", "RelianClient/0.1.0").GET().build();
        HttpResponse<String> metadataResponse = HTTP.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
        if (metadataResponse.statusCode() != 200)
            throw new IOException("CurseForge could not resolve mod file " + fileId + " (HTTP " + metadataResponse.statusCode() + ").");
        JsonObject data;
        try { data = JsonParser.parseString(metadataResponse.body()).getAsJsonObject().getAsJsonObject("data"); }
        catch (RuntimeException ex) { throw new IOException("CurseForge returned invalid file metadata.", ex); }
        if (data == null || (data.has("isAvailable") && !data.get("isAvailable").getAsBoolean()))
            throw new IOException("A required CurseForge mod file is no longer available.");
        String fileName = safeFileName(string(data, "fileName"));
        String url = string(data, "downloadUrl");
        String sha1 = sha1(array(data, "hashes"));
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar") || url.isBlank() || sha1.isBlank())
            throw new IOException("CurseForge did not provide a direct JAR download and SHA-1 checksum for a required mod.");
        URI downloadUri = URI.create(url);
        if (!trustedCdn(downloadUri)) throw new IOException("A CurseForge mod download used an untrusted URL.");

        Files.createDirectories(modsDirectory);
        String targetName = uniqueName(modsDirectory, fileName);
        Path destination = modsDirectory.resolve(targetName).normalize();
        if (!destination.startsWith(modsDirectory)) throw new IOException("Unsafe mod destination.");
        Path temporary = Files.createTempFile(modsDirectory, ".relian-cf-", ".part");
        boolean moved = false;
        try {
            HttpRequest request = HttpRequest.newBuilder(downloadUri).timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "RelianClient/0.1.0").GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 || !trustedCdn(response.uri())) {
                response.body().close();
                throw new IOException("A CurseForge mod download failed or redirected to an untrusted host.");
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_FILE) { response.body().close(); throw new IOException("A mod download exceeds 300 MB."); }
            MessageDigest digest = sha1Digest();
            long count = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    count += read;
                    totalBytes[0] += read;
                    if (count > MAX_FILE || totalBytes[0] > MAX_TOTAL) throw new IOException("CurseForge downloads exceed Relian's safety limits.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha1))
                throw new IOException("SHA-1 verification failed for required mod " + fileName + ".");
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static int extractOverrides(ZipFile zip, Path gameRoot) throws IOException {
        int count = 0;
        long total = 0;
        int entries = 0;
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
            ZipEntry entry = all.nextElement();
            if (++entries > MAX_ENTRIES) throw new IOException("The modpack ZIP contains too many entries.");
            String name = entry.getName();
            if ("manifest.json".equals(name) || "modlist.html".equals(name)) continue;
            if (!name.startsWith("overrides/")) throw new IOException("Unsupported file outside the manifest and overrides folder: " + name);
            String relative = name.substring("overrides/".length());
            if (relative.isBlank()) continue;
            validateRelative(relative);
            Path target = gameRoot.resolve(relative).normalize();
            if (!target.startsWith(gameRoot) || target.equals(gameRoot)) throw new IOException("Unsafe override path.");
            if (entry.isDirectory()) {
                String directoryRoot = relative.contains("/") ? relative.substring(0, relative.indexOf("/")) : relative;
                if (!FOLDERS.contains(directoryRoot)) throw new IOException("Unsupported override directory: " + relative);
                Files.createDirectories(target);
                continue;
            }
            String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
            if (!FOLDERS.contains(first) && !FILES.contains(relative))
                throw new IOException("Unsupported override path: " + relative);
            long size = entry.getSize();
            if (size < 0 || size > MAX_FILE) throw new IOException("An override file has an unknown or excessive size.");
            total += size;
            if (total > MAX_TOTAL) throw new IOException("Modpack overrides exceed Relian's 2 GB safety limit.");
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                int read; long written = 0;
                while ((read = in.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_FILE || written > size) throw new IOException("An override expanded beyond its declared size.");
                    out.write(buffer, 0, read);
                }
                if (written != size) throw new IOException("An override file did not match its declared size.");
            }
            count++;
        }
        return count;
    }

    private static void validateRelative(String value) throws IOException {
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\") || value.contains(":")
                || value.indexOf('\0') >= 0) throw new IOException("The archive contains an unsafe override path.");
        for (String part : value.split("/")) {
            if (part.isBlank() || part.equals(".") || part.equals("..")) throw new IOException("The archive contains an unsafe override path.");
        }
    }

    private static void writeMetadata(Path root, PackInfo info, String archiveName, int required, int overrides) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("format", "curseforge");
        metadata.addProperty("archive", archiveName);
        metadata.addProperty("name", info.name());
        metadata.addProperty("minecraftVersion", info.minecraftVersion());
        metadata.addProperty("loader", info.loader());
        metadata.addProperty("loaderVersion", info.loaderVersion());
        metadata.addProperty("requiredModFiles", required);
        metadata.addProperty("overrideFiles", overrides);
        Files.writeString(root.resolve("relian-curseforge-modpack.json"), metadata.toString(),
                StandardCharsets.UTF_8);
    }

    private static boolean trustedCdn(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase("forgecdn.net") || host.endsWith(".forgecdn.net"));
    }
    private static String sha1(JsonArray hashes) {
        for (JsonElement element : hashes) if (element.isJsonObject()) {
            JsonObject hash = element.getAsJsonObject();
            if (hash.has("algo") && hash.get("algo").getAsInt() == 1) return string(hash, "value");
        }
        return "";
    }
    private static MessageDigest sha1Digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-1 is unavailable.", ex); }
    }
    private static String uniqueName(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        if (!Files.exists(candidate)) return fileName;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int index = 2;
        while (Files.exists(dir.resolve(base + "-" + index + ext))) index++;
        return base + "-" + index + ext;
    }
    private static String safeFileName(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[. ]+$", "").trim();
    }
    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }
    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
    private static int integer(JsonObject object, String key) throws IOException {
        try { return object.get(key).getAsInt(); }
        catch (RuntimeException ex) { throw new IOException("The CurseForge manifest contains an invalid " + key + ".", ex); }
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static void deleteTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
