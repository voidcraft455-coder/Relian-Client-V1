package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.relian.launcher.profiles.InstanceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Safely imports a local Modrinth .mrpack into a new, isolated Relian instance.
 *
 * The importer handles the Modrinth formatVersion 1 manifest, client files and
 * overrides. Loader binaries are deliberately not installed here; the exact
 * loader metadata is preserved for the later loader-installation milestone.
 */
public final class ModrinthModpackImporter {
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_DOWNLOAD_BYTES = 300L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_COMPRESSION_RATIO = 500L;
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final int MAX_PACK_FILES = 5_000;
    private static final int MAX_MANIFEST_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private static final Set<String> ALLOWED_GAME_FOLDERS = Set.of(
            "mods", "config", "resourcepacks", "shaderpacks", "defaultconfigs",
            "kubejs", "scripts", "datapacks", "global_packs", "patchouli_books",
            "journeymap", "xaero", "schematics", "saves"
    );
    private static final Set<String> ALLOWED_GAME_FILES = Set.of(
            "options.txt", "optionsof.txt", "optionsshaders.txt"
    );
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ModrinthModpackImporter() {}

    public record PackMetadata(
            String name,
            String versionId,
            String summary,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            int clientFileCount) {}

    private record PackFile(
            String path,
            List<URI> downloads,
            long fileSize,
            String sha1,
            String sha512) {}

    private record ArchiveEntry(ZipEntry zipEntry, String name, String relativePath, int overridePriority) {}

    private record WrittenFile(String path, int priority) {}

    /**
     * Imports the archive into a temporary sibling directory and publishes the
     * instance only after every archive entry and required client file succeeds.
     */
    public static InstanceManager.CreatedInstance importPack(
            Path archive, Path instancesRoot, String instanceName)
            throws IOException, InterruptedException {
        if (archive == null || instancesRoot == null) {
            throw new IllegalArgumentException("Choose a .mrpack file and an instances folder.");
        }

        Path source = archive.toAbsolutePath().normalize();
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
            throw new IOException("Choose a Modrinth .mrpack archive.");
        }
        if (Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected .mrpack file is not a regular, safe file.");
        }
        long archiveSize = Files.size(source);
        if (archiveSize <= 0 || archiveSize > MAX_ARCHIVE_BYTES) {
            throw new IOException("The .mrpack archive must be between 1 byte and 512 MB.");
        }

        Map<String, Integer> unixModes = readCentralDirectoryModes(source);
        List<ArchiveEntry> archiveEntries = new ArrayList<>();
        String manifestJson = null;
        long archiveUncompressedBytes = 0;
        Set<String> archiveNames = new HashSet<>();

        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            int count = 0;
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++count > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("The .mrpack archive contains too many entries (limit: "
                            + MAX_ARCHIVE_ENTRIES + ").");
                }

                String name = entry.getName();
                validateArchiveEntryName(name, entry.isDirectory());
                String canonicalName = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                if (!archiveNames.add(canonicalName.toLowerCase(Locale.ROOT))) {
                    throw new IOException("The archive contains duplicate or case-colliding paths: " + name);
                }

                Integer mode = unixModes.get(name);
                if (mode == null) {
                    throw new IOException("The archive central directory is incomplete.");
                }
                if (isUnsafeUnixMode(mode)) {
                    throw new IOException("The archive contains a symbolic link or special file: " + name);
                }

                long size = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (size < 0 || compressedSize < 0 || size > MAX_ENTRY_BYTES) {
                    throw new IOException("An archive entry has an unknown or excessive size: " + name);
                }
                if (size > 0 && (compressedSize == 0 || size > compressedSize * MAX_COMPRESSION_RATIO)) {
                    throw new IOException("An archive entry has an unsafe compression ratio: " + name);
                }
                archiveUncompressedBytes = checkedAdd(archiveUncompressedBytes, size, MAX_TOTAL_BYTES,
                        "The archive expands beyond Relian's 2 GB safety limit.");

                int method = entry.getMethod();
                if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                    throw new IOException("Unsupported ZIP compression method in " + name + ".");
                }

                if ("modrinth.index.json".equals(name)) {
                    if (entry.isDirectory() || size > MAX_MANIFEST_BYTES) {
                        throw new IOException("modrinth.index.json must be a regular file no larger than 5 MB.");
                    }
                    manifestJson = readUtf8Limited(zip.getInputStream(entry), MAX_MANIFEST_BYTES);
                    continue;
                }

                if ("overrides/".equals(name) || "client-overrides/".equals(name)) {
                    if (!entry.isDirectory()) throw new IOException("Invalid overrides directory entry.");
                    continue;
                }

                int priority;
                String relativePath;
                if (name.startsWith("overrides/")) {
                    priority = 2;
                    relativePath = name.substring("overrides/".length());
                } else if (name.startsWith("client-overrides/")) {
                    priority = 3;
                    relativePath = name.substring("client-overrides/".length());
                } else {
                    throw new IOException("Unsupported .mrpack archive entry outside the manifest and override folders: " + name);
                }

                if (!relativePath.isEmpty()) {
                    validateGamePath(relativePath, entry.isDirectory());
                    archiveEntries.add(new ArchiveEntry(entry, name, relativePath, priority));
                } else if (!entry.isDirectory()) {
                    throw new IOException("An overrides root must be a directory.");
                }
            }

            if (manifestJson == null) {
                throw new IOException("The archive does not contain a root modrinth.index.json manifest.");
            }
            if (count != unixModes.size()) {
                throw new IOException("The archive contains inconsistent central-directory metadata.");
            }

            PackMetadata metadata = parseManifest(manifestJson);
            List<PackFile> clientFiles = parseClientFiles(manifestJson);
            long knownDownloadBytes = 0;
            for (PackFile file : clientFiles) {
                if (file.fileSize() >= 0) {
                    knownDownloadBytes = checkedAdd(knownDownloadBytes, file.fileSize(), MAX_TOTAL_BYTES,
                            "The modpack's declared downloads exceed Relian's 2 GB safety limit.");
                }
            }
            if (checkedAdd(archiveUncompressedBytes, knownDownloadBytes, MAX_TOTAL_BYTES,
                    "The archive and its downloads exceed Relian's 2 GB safety limit.") > MAX_TOTAL_BYTES) {
                throw new IOException("The archive and its downloads exceed Relian's 2 GB safety limit.");
            }

            String cleanName = InstanceManager.normalizeInstanceName(instanceName);
            Path root = instancesRoot.toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The selected instances location is not a safe directory.");
            }
            root = root.toRealPath();
            Path finalTarget = root.resolve(cleanName).normalize();
            if (!finalTarget.startsWith(root) || finalTarget.equals(root)) {
                throw new IOException("The requested instance destination is unsafe.");
            }
            if (Files.exists(finalTarget, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("An instance with this name already exists. Choose a different name; existing files were not changed.");
            }

            Path stagingRoot = Files.createTempDirectory(root, ".relian-import-" + UUID.randomUUID() + "-");
            InstanceManager.CreatedInstance staged = null;
            boolean published = false;
            try {
                staged = InstanceManager.create(stagingRoot, cleanName, metadata.minecraftVersion(),
                        metadata.loader(), InstanceManager.Kind.MODPACK, null, metadata.name());
                Path gameRoot = staged.directory();
                Map<String, WrittenFile> writtenFiles = new HashMap<>();
                long[] totalBytes = {archiveUncompressedBytes};

                // The Modrinth file list is installed first; ordinary overrides
                // are applied before client-overrides regardless of ZIP entry order.
                archiveEntries.sort(java.util.Comparator.comparingInt(ArchiveEntry::overridePriority));
                for (PackFile file : clientFiles) {
                    downloadFile(file, gameRoot, writtenFiles, totalBytes);
                }
                for (ArchiveEntry entry : archiveEntries) {
                    if (!entry.zipEntry().isDirectory()) {
                        extractArchiveFile(zip, entry, gameRoot, writtenFiles, totalBytes);
                    } else {
                        createSafeDirectories(gameRoot, entry.relativePath());
                    }
                }

                InstanceManager.applyPerformanceDefaults(gameRoot, metadata.minecraftVersion());
                writePackMetadata(gameRoot, metadata, source.getFileName().toString(),
                        clientFiles.size(), countInstalledFiles(writtenFiles));

                if (Files.exists(finalTarget, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("An instance with this name was created while the import was running. Choose another name.");
                }
                Files.move(staged.directory(), finalTarget);
                published = true;
                return new InstanceManager.CreatedInstance(finalTarget, staged.manifest());
            } catch (IOException | InterruptedException | RuntimeException ex) {
                if (published && Files.exists(finalTarget, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        InstanceManager.delete(root, finalTarget);
                    } catch (IOException cleanup) {
                        ex.addSuppressed(cleanup);
                    }
                } else if (staged != null && Files.exists(staged.directory(), LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        InstanceManager.delete(stagingRoot, staged.directory());
                    } catch (IOException cleanup) {
                        ex.addSuppressed(cleanup);
                    }
                }
                throw ex;
            } finally {
                deleteTreeQuietly(stagingRoot);
            }
        }
    }

    static PackMetadata parseManifest(String json) throws IOException {
        JsonObject manifest;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new IOException("The Modrinth manifest must be a JSON object.");
            manifest = parsed.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("The Modrinth manifest is not valid JSON.", ex);
        }

        int formatVersion = integer(manifest, "formatVersion", -1);
        if (formatVersion != 1) {
            throw new IOException("Unsupported Modrinth modpack formatVersion. Relian supports formatVersion 1.");
        }
        if (!"minecraft".equalsIgnoreCase(string(manifest, "game"))) {
            throw new IOException("This .mrpack is not a Minecraft modpack.");
        }

        String name = requiredText(manifest, "name", 128);
        String versionId = optionalText(manifest, "versionId", 128);
        String summary = optionalText(manifest, "summary", 512);

        JsonObject dependencies = object(manifest, "dependencies");
        String minecraftVersion = safeVersion(requiredText(dependencies, "minecraft", 64), "Minecraft");
        String loader = "Vanilla";
        String loaderVersion = "";
        String[][] loaderKeys = {
                {"fabric-loader", "Fabric"},
                {"quilt-loader", "Quilt"},
                {"forge", "Forge"},
                {"neoforge", "NeoForge"}
        };
        int foundLoaders = 0;
        for (String[] loaderKey : loaderKeys) {
            if (dependencies.has(loaderKey[0]) && !dependencies.get(loaderKey[0]).isJsonNull()) {
                foundLoaders++;
                loader = loaderKey[1];
                loaderVersion = safeVersion(dependencies.get(loaderKey[0]).getAsString(), loader);
            }
        }
        if (foundLoaders > 1) {
            throw new IOException("The modpack declares more than one loader. Relian cannot safely choose between them.");
        }

        List<PackFile> files = parseClientFiles(json);
        return new PackMetadata(name, versionId, summary, minecraftVersion, loader, loaderVersion, files.size());
    }

    private static List<PackFile> parseClientFiles(String json) throws IOException {
        JsonObject manifest;
        try {
            manifest = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("The Modrinth manifest is not valid JSON.", ex);
        }
        if (!manifest.has("files") || !manifest.get("files").isJsonArray()) {
            throw new IOException("The Modrinth manifest is missing its files array.");
        }
        JsonArray files = manifest.getAsJsonArray("files");
        if (files.size() > MAX_PACK_FILES) throw new IOException("The modpack lists too many files.");

        List<PackFile> result = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (JsonElement element : files) {
            if (!element.isJsonObject()) throw new IOException("A Modrinth file entry is not a JSON object.");
            JsonObject file = element.getAsJsonObject();
            String path = requiredText(file, "path", 240);
            validateGamePath(path, false);
            if (!seenPaths.add(path.toLowerCase(Locale.ROOT))) {
                throw new IOException("The manifest contains duplicate or case-colliding file paths: " + path);
            }

            String clientEnvironment = "required";
            if (file.has("env") && !file.get("env").isJsonNull()) {
                if (!file.get("env").isJsonObject()) throw new IOException("The manifest has an invalid environment object for " + path + ".");
                clientEnvironment = optionalText(file.getAsJsonObject("env"), "client", 32);
                if (clientEnvironment.isBlank()) clientEnvironment = "required";
            }
            if (!clientEnvironment.equals("required") && !clientEnvironment.equals("optional")
                    && !clientEnvironment.equals("unsupported")) {
                throw new IOException("The manifest has an unknown client environment value for " + path + ".");
            }

            long fileSize = -1;
            if (file.has("fileSize") && !file.get("fileSize").isJsonNull()) {
                try {
                    if (!file.get("fileSize").isJsonPrimitive()
                            || !file.getAsJsonPrimitive("fileSize").isNumber()) {
                        throw new IllegalStateException("fileSize must be an integer.");
                    }
                    fileSize = file.get("fileSize").getAsLong();
                } catch (RuntimeException ex) {
                    throw new IOException("The manifest has an invalid fileSize for " + path + ".", ex);
                }
                if (fileSize < 0 || fileSize > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("A modpack file exceeds Relian's 300 MB per-file download limit: " + path);
                }
            }

            if (file.has("hashes") && !file.get("hashes").isJsonNull() && !file.get("hashes").isJsonObject()) {
                throw new IOException("The manifest has an invalid hashes object for " + path + ".");
            }
            JsonObject hashes = file.has("hashes") && file.get("hashes").isJsonObject()
                    ? file.getAsJsonObject("hashes") : new JsonObject();
            for (String algorithm : hashes.keySet()) {
                if (!algorithm.equals("sha1") && !algorithm.equals("sha512")) {
                    throw new IOException("The manifest uses an unsupported integrity hash for " + path + ": " + algorithm);
                }
            }
            String sha1 = hash(hashes, "sha1", 40, path);
            String sha512 = hash(hashes, "sha512", 128, path);

            if (!"required".equals(clientEnvironment)) {
                continue;
            }

            if (!file.has("downloads") || !file.get("downloads").isJsonArray()) {
                throw new IOException("A client-required file has an invalid downloads list: " + path);
            }
            JsonArray downloads = file.getAsJsonArray("downloads");
            if (downloads.isEmpty()) {
                throw new IOException("A client-required file has no download URL: " + path);
            }
            List<URI> safeDownloads = new ArrayList<>();
            for (JsonElement download : downloads) {
                if (!download.isJsonPrimitive()) throw new IOException("A download URL is invalid for " + path + ".");
                safeDownloads.add(validateHttpsUri(download.getAsString()));
            }
            result.add(new PackFile(path, List.copyOf(safeDownloads), fileSize, sha1, sha512));
        }
        return List.copyOf(result);
    }

    private static void downloadFile(
            PackFile file, Path gameRoot, Map<String, WrittenFile> writtenFiles, long[] totalBytes)
            throws IOException, InterruptedException {
        URI uri = file.downloads().get(0);
        Path destination = prepareDestination(gameRoot, file.path(), writtenFiles, 1);
        Path parent = destination.getParent();
        Path temporary = Files.createTempFile(parent, ".relian-download-", ".part");
        boolean installed = false;
        try {
            HttpResponse<InputStream> response = openVerifiedHttpsDownload(uri);
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_DOWNLOAD_BYTES
                    || (file.fileSize() >= 0 && declaredLength >= 0 && declaredLength != file.fileSize())) {
                response.body().close();
                throw new IOException("The download size does not match the modpack manifest for " + file.path() + ".");
            }

            MessageDigest sha1 = file.sha1().isBlank() ? null : digest("SHA-1");
            MessageDigest sha512 = file.sha512().isBlank() ? null : digest("SHA-512");
            long copied = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    copied = checkedAdd(copied, read, MAX_DOWNLOAD_BYTES,
                            "A modpack download exceeded Relian's 300 MB per-file limit.");                    out.write(buffer, 0, read);
                }
            }
            if (file.fileSize() >= 0 && copied != file.fileSize()) {
                throw new IOException("The downloaded size does not match the manifest for " + file.path() + ".");
            }
            if (sha1 != null && !HexFormat.of().formatHex(sha1.digest()).equalsIgnoreCase(file.sha1())) {
                throw new IOException("SHA-1 verification failed for " + file.path() + ". The file was not installed.");
            }
            if (sha512 != null && !HexFormat.of().formatHex(sha512.digest()).equalsIgnoreCase(file.sha512())) {
                throw new IOException("SHA-512 verification failed for " + file.path() + ". The file was not installed.");
            }

            publishFile(temporary, destination, file.path(), writtenFiles, 1);
            installed = true;
        } finally {
            if (!installed) Files.deleteIfExists(temporary);
        }
    }

    private static HttpResponse<InputStream> openVerifiedHttpsDownload(URI initial)
            throws IOException, InterruptedException {
        URI current = validateHttpsUri(initial.toString());
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            rejectPrivateDestination(current.getHost());
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "RelianClient/0.1.0 (Modrinth modpack importer)")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse("");
                response.body().close();
                if (location.isBlank() || redirects == MAX_REDIRECTS) {
                    throw new IOException("The modpack download returned an invalid or excessive redirect.");
                }
                try {
                    current = validateHttpsUri(current.resolve(location).toString());
                } catch (IllegalArgumentException ex) {
                    throw new IOException("The modpack download redirected to an unsafe URL.", ex);
                }
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("The modpack download failed with HTTP " + status + ".");
            }
            try {
                validateHttpsUri(response.uri().toString());
                rejectPrivateDestination(response.uri().getHost());
            } catch (IOException ex) {
                response.body().close();
                throw ex;
            }
            return response;
        }
        throw new IOException("The modpack download exceeded the redirect limit.");
    }

    static URI validateHttpsUri(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.length() > 2_048) {
            throw new IOException("A modpack download URL is empty or too long.");
        }
        try {
            URI uri = URI.create(raw.trim());
            String host = uri.getHost();
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || host == null || host.isBlank() || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) {
                throw new IOException("Modpack files must use direct HTTPS URLs on the standard secure port.");
            }
            String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            if (asciiHost.endsWith(".")) asciiHost = asciiHost.substring(0, asciiHost.length() - 1);
            if (asciiHost.matches("[0-9.]+") || asciiHost.contains(":")
                    || asciiHost.equals("localhost") || asciiHost.endsWith(".localhost")
                    || asciiHost.endsWith(".local") || asciiHost.endsWith(".internal")
                    || asciiHost.endsWith(".lan") || asciiHost.endsWith(".test")) {
                throw new IOException("The modpack download URL uses a local or non-public host.");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IOException("A modpack download URL is malformed.", ex);
        }
    }

    private static boolean isPrivateOrReservedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 0xff, b = bytes[1] & 0xff;
            if (a == 0 || a == 10 || a == 127 || a >= 224 || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                    || (a == 100 && b >= 64 && b <= 127) || (a == 192 && b == 0)
                    || (a == 198 && (b == 18 || b == 19 || b == 51 || b == 100))
                    || (a == 203 && b == 0)) return true;
        } else if (bytes.length == 16 && ((bytes[0] & 0xfe) == 0xfc
                || ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80)
                || ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8))) {
            return true;
        }
        return false;
    }

    private static void rejectPrivateDestination(String host) throws IOException {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateOrReservedAddress(address)) {
                    throw new IOException("The modpack download resolves to a private or local network address.");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw new IOException("The modpack download host could not be resolved.", ex);
        }
    }

    private static void extractArchiveFile(
            ZipFile zip, ArchiveEntry entry, Path gameRoot, Map<String, WrittenFile> writtenFiles, long[] totalBytes)
            throws IOException {
        Path destination = prepareDestination(gameRoot, entry.relativePath(), writtenFiles, entry.overridePriority());
        Path temporary = Files.createTempFile(destination.getParent(), ".relian-override-", ".part");
        boolean installed = false;
        try (InputStream in = zip.getInputStream(entry.zipEntry());
             OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[16 * 1024];
            long copied = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                copied = checkedAdd(copied, read, MAX_ENTRY_BYTES,
                        "An override file exceeded Relian's 256 MB per-entry limit.");
                totalBytes[0] = checkedAdd(totalBytes[0], read, MAX_TOTAL_BYTES,
                        "The complete modpack import exceeded Relian's 2 GB safety limit.");
                out.write(buffer, 0, read);
            }
            if (copied != entry.zipEntry().getSize()) {
                throw new IOException("An override entry did not match its declared ZIP size: " + entry.name());
            }
            publishFile(temporary, destination, entry.relativePath(), writtenFiles, entry.overridePriority());
            installed = true;
        } finally {
            if (!installed) Files.deleteIfExists(temporary);
        }
    }

    private static Path prepareDestination(
            Path gameRoot, String relativePath, Map<String, WrittenFile> writtenFiles, int priority)
            throws IOException {
        validateGamePath(relativePath, false);
        Path root = gameRoot.toAbsolutePath().normalize();
        Path destination = root.resolve(relativePath).normalize();
        if (!destination.startsWith(root) || destination.equals(root)) {
            throw new IOException("A modpack file resolves outside its isolated instance.");
        }
        createSafeDirectories(gameRoot, relativePath.substring(0, Math.max(0, relativePath.lastIndexOf('/'))));
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("A modpack destination is a symbolic link: " + relativePath);
        }

        String key = relativePath.toLowerCase(Locale.ROOT);
        WrittenFile previous = writtenFiles.get(key);
        if (previous != null) {
            if (!previous.path().equals(relativePath)) {
                throw new IOException("The modpack contains paths that collide on Windows: " + relativePath);
            }
            if (priority <= previous.priority()) {
                throw new IOException("The modpack contains duplicate destination files: " + relativePath);
            }
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A modpack destination is not a regular file: " + relativePath);
            }
        } else if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!ALLOWED_GAME_FILES.contains(relativePath.toLowerCase(Locale.ROOT))
                    || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Relian will not overwrite an existing instance file: " + relativePath);
            }
        }
        return destination;
    }

    private static void publishFile(
            Path temporary, Path destination, String relativePath,
            Map<String, WrittenFile> writtenFiles, int priority) throws IOException {
        String key = relativePath.toLowerCase(Locale.ROOT);
        boolean replace = writtenFiles.containsKey(key)
                || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && ALLOWED_GAME_FILES.contains(key));
        try {
            if (replace) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, destination);
            }
        } catch (java.nio.file.FileAlreadyExistsException ex) {
            throw new IOException("Relian refused to overwrite an existing instance file: " + relativePath, ex);
        }
        writtenFiles.put(key, new WrittenFile(relativePath, priority));
    }

    private static void createSafeDirectories(Path gameRoot, String relativeDirectory) throws IOException {
        Path root = gameRoot.toAbsolutePath().normalize();
        if (relativeDirectory == null || relativeDirectory.isBlank()) return;
        validateGamePath(relativeDirectory, true);
        Path current = root;
        for (String segment : relativeDirectory.split("/")) {
            current = current.resolve(segment).normalize();
            if (!current.startsWith(root)) throw new IOException("A modpack directory resolves outside its instance.");
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("A modpack destination directory is not safe: " + current.getFileName());
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    static String validateGamePath(String rawPath, boolean directory) throws IOException {
        if (rawPath == null || rawPath.isBlank() || rawPath.length() > 240
                || rawPath.startsWith("/") || rawPath.startsWith("\\")
                || rawPath.contains("\\") || rawPath.indexOf('\0') >= 0
                || rawPath.matches("^[A-Za-z]:.*")) {
            throw new IOException("The modpack contains an absolute, empty, or invalid path.");
        }
        String path = directory && rawPath.endsWith("/")
                ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
        if (path.isBlank() || path.endsWith("/") || path.endsWith(".")) {
            throw new IOException("The modpack contains an invalid destination path: " + rawPath);
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.length() > 120 || segment.endsWith(".") || segment.endsWith(" ")
                    || segment.startsWith(".") || segment.matches(".*[<>:\"|?*\\p{Cntrl}].*")) {
                throw new IOException("The modpack contains an unsafe path segment: " + segment);
            }
            String deviceName = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (WINDOWS_RESERVED_NAMES.contains(deviceName)) {
                throw new IOException("The modpack contains a reserved Windows path name: " + segment);
            }
        }

        String top = segments[0].toLowerCase(Locale.ROOT);
        if (segments.length == 1) {
            if (directory) {
                if (ALLOWED_GAME_FOLDERS.contains(top)) return path;
                throw new IOException("A modpack directory cannot use a protected game-root filename: " + path);
            }
            if (!ALLOWED_GAME_FILES.contains(top)) {
                throw new IOException("The modpack targets a protected or unsupported game-root file: " + path);
            }
        } else if (!ALLOWED_GAME_FOLDERS.contains(top)) {
            throw new IOException("The modpack targets a protected or unsupported game folder: " + top);
        }
        return path;
    }

    private static void validateArchiveEntryName(String name, boolean directory) throws IOException {
        if (name == null || name.isBlank() || name.length() > 260
                || name.startsWith("/") || name.startsWith("\\") || name.contains("\\")
                || name.indexOf('\0') >= 0 || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("The .mrpack archive contains an absolute or invalid entry path.");
        }
        String checked = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (checked.isBlank()) throw new IOException("The .mrpack archive contains an empty path.");
        for (String segment : checked.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")
                    || segment.matches(".*[<>:\"|?*\\p{Cntrl}].*")) {
                throw new IOException("The .mrpack archive contains a path-traversal or unsafe entry: " + name);
            }
        }
    }

    private static Map<String, Integer> readCentralDirectoryModes(Path archive) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
            long length = file.length();
            long searchStart = Math.max(0, length - 65_557);
            long eocd = -1;
            for (long position = length - 22; position >= searchStart; position--) {
                file.seek(position);
                if (readU32LE(file) == 0x06054b50L) {
                    file.seek(position + 20);
                    int commentLength = readU16LE(file);
                    if (position + 22L + commentLength == length) {
                        eocd = position;
                        break;
                    }
                }
            }
            if (eocd < 0) throw new IOException("The .mrpack archive has no valid ZIP central directory.");

            file.seek(eocd + 4);
            int diskNumber = readU16LE(file);
            int centralDisk = readU16LE(file);
            int entriesOnDisk = readU16LE(file);
            int totalEntries = readU16LE(file);
            long centralSize = readU32LE(file);
            long centralOffset = readU32LE(file);
            readU16LE(file); // ZIP comment length

            if (diskNumber != 0 || centralDisk != 0 || entriesOnDisk != totalEntries
                    || totalEntries == 0xffff || centralSize == 0xffffffffL
                    || centralOffset == 0xffffffffL || totalEntries > MAX_ARCHIVE_ENTRIES
                    || centralOffset + centralSize > eocd) {
                throw new IOException("Multi-disk and ZIP64 archives are not supported by the safe .mrpack importer.");
            }

            Map<String, Integer> modes = new HashMap<>();
            Set<String> names = new HashSet<>();
            file.seek(centralOffset);
            for (int index = 0; index < totalEntries; index++) {
                if (readU32LE(file) != 0x02014b50L) {
                    throw new IOException("The .mrpack ZIP central directory is malformed.");
                }
                int madeBy = readU16LE(file);
                readU16LE(file); // version needed
                int flags = readU16LE(file);
                int method = readU16LE(file);
                readU16LE(file); // modification time
                readU16LE(file); // modification date
                readU32LE(file); // CRC-32
                readU32LE(file); // compressed size
                readU32LE(file); // uncompressed size
                int nameLength = readU16LE(file);
                int extraLength = readU16LE(file);
                int commentLength = readU16LE(file);
                int diskStart = readU16LE(file);
                readU16LE(file); // internal attributes
                long externalAttributes = readU32LE(file);
                readU32LE(file); // local header offset

                if ((flags & 1) != 0 || (method != ZipEntry.STORED && method != ZipEntry.DEFLATED)
                        || diskStart != 0) {
                    throw new IOException("Encrypted, split, or unsupported ZIP entries are not accepted.");
                }
                byte[] nameBytes = new byte[nameLength];
                file.readFully(nameBytes);
                String name;
                try {
                    name = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(nameBytes)).toString();
                } catch (CharacterCodingException ex) {
                    throw new IOException("The .mrpack archive contains a non-UTF-8 entry name.", ex);
                }
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("The .mrpack archive contains duplicate or case-colliding paths: " + name);
                }

                int hostSystem = (madeBy >>> 8) & 0xff;
                int unixMode = (int) ((externalAttributes >>> 16) & 0xffff);
                if ((externalAttributes & 0x400L) != 0) {
                    throw new IOException("The .mrpack archive contains a Windows reparse-point entry: " + name);
                }
                if ((hostSystem == 3 || hostSystem == 19) && isUnsafeUnixMode(unixMode)) {
                    throw new IOException("The .mrpack archive contains a symbolic link or special file: " + name);
                }
                if (hostSystem == 3 || hostSystem == 19) {
                    int unixType = unixMode & 0170000;
                    if ((unixType == 0040000 && !name.endsWith("/"))
                            || (unixType == 0100000 && name.endsWith("/"))) {
                        throw new IOException("The ZIP entry type does not match its path: " + name);
                    }
                }
                modes.put(name, unixMode);
                file.seek(file.getFilePointer() + extraLength + commentLength);
            }
            if (file.getFilePointer() != centralOffset + centralSize) {
                throw new IOException("The .mrpack ZIP central directory has unexpected trailing data.");
            }
            return Collections.unmodifiableMap(modes);
        }
    }

    static boolean isUnsafeUnixMode(int mode) {
        int type = mode & 0170000;
        return type == 0120000 || (type != 0 && type != 0100000 && type != 0040000);
    }

    private static int readU16LE(RandomAccessFile file) throws IOException {
        int first = file.read();
        int second = file.read();
        if (first < 0 || second < 0) throw new IOException("Unexpected end of ZIP central directory.");
        return first | (second << 8);
    }

    private static long readU32LE(RandomAccessFile file) throws IOException {
        long first = readU16LE(file);
        long second = readU16LE(file);
        return first | (second << 16);
    }

    private static String readUtf8Limited(InputStream input, int maximum) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > maximum) throw new IOException("The Modrinth manifest exceeds the 5 MB limit.");
                out.write(buffer, 0, read);
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("The Modrinth manifest is not valid UTF-8.", ex);
        }
    }

    private static void writePackMetadata(
            Path gameRoot, PackMetadata metadata, String archiveName, int clientFiles, int installedFiles)
            throws IOException {
        JsonObject details = new JsonObject();
        details.addProperty("formatVersion", 1);
        details.addProperty("name", metadata.name());
        details.addProperty("versionId", metadata.versionId());
        details.addProperty("summary", metadata.summary());
        details.addProperty("minecraftVersion", metadata.minecraftVersion());
        details.addProperty("loader", metadata.loader());
        details.addProperty("loaderVersion", metadata.loaderVersion());
        details.addProperty("sourceArchive", archiveName);
        details.addProperty("clientFilesDeclared", clientFiles);
        details.addProperty("filesInstalled", installedFiles);
        details.addProperty("importedAt", Instant.now().toString());
        Files.writeString(gameRoot.resolve("relian-modpack.json"), GSON.toJson(details),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static int countInstalledFiles(Map<String, WrittenFile> writtenFiles) {
        return writtenFiles.size();
    }

    private static String hash(JsonObject hashes, String key, int expectedLength, String path) throws IOException {
        if (!hashes.has(key) || hashes.get(key).isJsonNull()) return "";
        String value;
        try {
            value = hashes.get(key).getAsString().trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            throw new IOException("The manifest contains an invalid " + key.toUpperCase(Locale.ROOT)
                    + " hash for " + path + ".", ex);
        }
        if (!value.matches("[a-f0-9]{" + expectedLength + "}")) {
            throw new IOException("The manifest contains an invalid " + key.toUpperCase(Locale.ROOT)
                    + " hash for " + path + ".");
        }
        return value;
    }

    private static MessageDigest digest(String algorithm) throws IOException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(algorithm + " is unavailable in this Java runtime.", ex);
        }
    }

    private static long checkedAdd(long current, long increment, long limit, String message) throws IOException {
        if (increment < 0 || current > limit - increment) throw new IOException(message);
        return current + increment;
    }

    private static JsonObject object(JsonObject parent, String key) throws IOException {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            throw new IOException("The Modrinth manifest is missing its " + key + " object.");
        }
        return parent.getAsJsonObject(key);
    }

    private static String requiredText(JsonObject object, String key, int maxLength) throws IOException {
        String value = optionalText(object, key, maxLength);
        if (value.isBlank()) throw new IOException("The Modrinth manifest is missing " + key + ".");
        return value;
    }

    private static String optionalText(JsonObject object, String key, int maxLength) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) return "";
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) throw new IOException("The Modrinth manifest has an invalid " + key + " value.");
        String value;
        try {
            value = element.getAsString().trim();
        } catch (RuntimeException ex) {
            throw new IOException("The Modrinth manifest has an invalid " + key + " value.", ex);
        }
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("The Modrinth manifest has an unsafe " + key + " value.");
        }
        return value;
    }

    private static String string(JsonObject object, String key) throws IOException {
        return optionalText(object, key, 128);
    }

    private static int integer(JsonObject object, String key, int fallback) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ex) {
            throw new IOException("The Modrinth manifest has an invalid " + key + " value.", ex);
        }
    }

    private static String safeVersion(String value, String label) throws IOException {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,63}")) {
            throw new IOException("The modpack has an invalid " + label + " version.");
        }
        return value;
    }
}
