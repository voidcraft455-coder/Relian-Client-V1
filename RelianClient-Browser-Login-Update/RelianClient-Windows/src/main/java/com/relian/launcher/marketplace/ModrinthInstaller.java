package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Installs a selected Modrinth mod/resource pack/shader after checking game compatibility
 * and verifying the downloaded file against the publisher-provided SHA-512 digest.
 */
public final class ModrinthInstaller {
    private static final URI API = URI.create("https://api.modrinth.com/v2");
    private static final long MAX_DOWNLOAD_BYTES = 300L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private ModrinthInstaller() {}

    public static Path install(MarketplaceProject project, String minecraftVersion, String loader, Path gameDirectory)
            throws IOException, InterruptedException {
        if (project == null || gameDirectory == null) throw new IllegalArgumentException("Project and game directory are required.");
        if (!"Modrinth".equalsIgnoreCase(project.source())) throw new IOException("Only Modrinth downloads are currently supported.");
        if (project.type() == ProjectType.MODPACK) {
            throw new IOException("Modpack import is not available yet. Open the project page to review its pack files.");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) throw new IOException("Choose a Minecraft version first.");

        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Choose an existing Minecraft game directory.");
        String loaderValue = project.type() == ProjectType.MOD ? normalizeLoader(loader) : "";
        String versionQuery = "[\"" + jsonEscape(minecraftVersion.trim()) + "\"]";
        String url = API + "/project/" + encodePath(project.id()) + "/version?game_versions="
                + enc(versionQuery);
        if (project.type() == ProjectType.MOD) {
            url += "&loaders=" + enc("[\"" + jsonEscape(loaderValue) + "\"]");
        }

        HttpRequest versionsRequest = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
        HttpResponse<String> versionsResponse = HTTP.send(versionsRequest, HttpResponse.BodyHandlers.ofString());
        if (versionsResponse.statusCode() != 200) {
            throw new IOException("Modrinth version lookup failed with HTTP " + versionsResponse.statusCode() + ".");
        }

        JsonArray versions;
        try {
            versions = JsonParser.parseString(versionsResponse.body()).getAsJsonArray();
        } catch (RuntimeException ex) {
            throw new IOException("Modrinth returned an invalid version list.", ex);
        }
        if (versions.isEmpty()) throw new IOException("No compatible files were found for Minecraft " + minecraftVersion + ".");

        JsonObject selectedVersion = versions.get(0).getAsJsonObject();
        String versionId = string(selectedVersion, "id");
        JsonArray files = selectedVersion.getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("The selected Modrinth version has no downloadable files.");

        JsonObject selectedFile = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                selectedFile = file;
                break;
            }
            if (selectedFile == null) selectedFile = file;
        }
        if (selectedFile == null) throw new IOException("No valid downloadable file was provided.");

        String fileName = safeFileName(string(selectedFile, "filename"));
        String downloadUrl = string(selectedFile, "url");
        JsonObject hashes = selectedFile.getAsJsonObject("hashes");
        String expectedHash = hashes == null ? "" : string(hashes, "sha512");
        if (fileName.isBlank() || downloadUrl.isBlank() || expectedHash.isBlank()) {
            throw new IOException("The selected file is missing its name, download URL, or SHA-512 checksum.");
        }

        URI downloadUri = URI.create(downloadUrl);
        String host = downloadUri.getHost();
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("cdn.modrinth.com") || host.endsWith(".modrinth.com"))) {
            throw new IOException("The download URL is not a trusted HTTPS Modrinth CDN URL.");
        }
        validateExtension(project.type(), fileName);

        Path targetDirectory = root.resolve(folderFor(project.type())).normalize();
        if (!targetDirectory.startsWith(root)) throw new IOException("Invalid installation directory.");
        Files.createDirectories(targetDirectory);
        String finalName = uniqueName(targetDirectory, fileName, versionId);
        Path destination = targetDirectory.resolve(finalName).normalize();
        if (!destination.startsWith(targetDirectory)) throw new IOException("Invalid target filename.");

        Path temporary = Files.createTempFile(targetDirectory, ".relian-download-", ".part");
        boolean moved = false;
        try {
            HttpRequest downloadRequest = HttpRequest.newBuilder(downloadUri).timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
            HttpResponse<InputStream> downloadResponse = HTTP.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (downloadResponse.statusCode() != 200) {
                downloadResponse.body().close();
                throw new IOException("Modrinth download failed with HTTP " + downloadResponse.statusCode() + ".");
            }
            long declaredLength = downloadResponse.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_DOWNLOAD_BYTES) {
                downloadResponse.body().close();
                throw new IOException("The download exceeds Relian's 300 MB safety limit.");
            }

            MessageDigest digest = sha512();
            long total = 0;
            try (InputStream in = downloadResponse.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) throw new IOException("The download exceeded Relian's 300 MB safety limit.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!actualHash.equalsIgnoreCase(expectedHash)) throw new IOException("SHA-512 verification failed. The file was not installed.");

            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            moved = true;
            return destination;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String folderFor(ProjectType type) {
        return switch (type) {
            case MOD -> "mods";
            case RESOURCE_PACK -> "resourcepacks";
            case SHADER -> "shaderpacks";
            case MODPACK -> throw new IllegalArgumentException("Modpacks are not installed as single files.");
        };
    }

    private static void validateExtension(ProjectType type, String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case MOD -> lower.endsWith(".jar");
            case RESOURCE_PACK, SHADER -> lower.endsWith(".zip");
            case MODPACK -> false;
        };
        if (!valid) throw new IOException("The downloaded file extension does not match this project type.");
    }

    private static String uniqueName(Path directory, String fileName, String versionId) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) return fileName;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        String suffix = versionId == null || versionId.isBlank() ? "relian" : versionId.replaceAll("[^A-Za-z0-9_-]", "");
        return base + "-" + suffix + extension;
    }

    private static String normalizeLoader(String loader) throws IOException {
        if (loader == null) throw new IOException("Choose a mod loader.");
        return switch (loader.trim().toLowerCase(Locale.ROOT)) {
            case "fabric" -> "fabric";
            case "forge" -> "forge";
            case "neoforge" -> "neoforge";
            case "quilt" -> "quilt";
            default -> throw new IOException("Choose Fabric, Forge, NeoForge, or Quilt for mods.");
        };
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String safeFileName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[. ]+$", "").trim();
        return cleaned.equals(".") || cleaned.equals("..") ? "" : cleaned;
    }
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String jsonEscape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static MessageDigest sha512() throws IOException {
        try { return MessageDigest.getInstance("SHA-512"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-512 is unavailable in this Java runtime.", ex); }
    }
}
