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

/** Installs CurseForge mod/resource-pack/shader files with API metadata and checksum validation. */
public final class CurseForgeInstaller {
    private static final URI API = URI.create("https://api.curseforge.com/v1");
    private static final long MAX_DOWNLOAD_BYTES = 300L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private CurseForgeInstaller() {}

    public static Path install(MarketplaceProject project, String minecraftVersion, String loader,
                               Path gameDirectory, String apiKey) throws IOException, InterruptedException {
        if (project == null || gameDirectory == null) throw new IllegalArgumentException("Project and game directory are required.");
        if (apiKey == null || apiKey.isBlank()) throw new IOException("CurseForge API key is not configured.");
        if (project.type() == ProjectType.MODPACK) throw new IOException("Modpack profile import is not available yet.");
        if (minecraftVersion == null || minecraftVersion.isBlank()) throw new IOException("Choose a Minecraft version first.");

        String url = API + "/mods/" + encode(project.id()) + "/files?gameVersion=" + enc(minecraftVersion.trim()) + "&pageSize=20&index=0";
        if (project.type() == ProjectType.MOD) {
            url += "&modLoaderType=" + loaderType(loader);
        }
        HttpRequest filesRequest = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey.trim()).header("Accept", "application/json").GET().build();
        HttpResponse<String> filesResponse = HTTP.send(filesRequest, HttpResponse.BodyHandlers.ofString());
        if (filesResponse.statusCode() != 200) throw new IOException("CurseForge file lookup failed with HTTP " + filesResponse.statusCode() + ".");

        JsonArray files;
        try {
            JsonObject root = JsonParser.parseString(filesResponse.body()).getAsJsonObject();
            files = root.getAsJsonArray("data");
        } catch (RuntimeException ex) {
            throw new IOException("CurseForge returned invalid file metadata.", ex);
        }
        if (files == null || files.isEmpty()) throw new IOException("No compatible CurseForge files were found for Minecraft " + minecraftVersion + ".");

        JsonObject selected = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (file.has("isAvailable") && !file.get("isAvailable").getAsBoolean()) continue;
            selected = file;
            break;
        }
        if (selected == null) throw new IOException("No available CurseForge file was found.");
        String fileName = safeFileName(string(selected, "fileName"));
        String downloadUrl = string(selected, "downloadUrl");
        String expectedHash = findSha1(selected.getAsJsonArray("hashes"));
        if (fileName.isBlank() || downloadUrl.isBlank() || expectedHash.isBlank()) {
            throw new IOException("CurseForge did not provide a direct download URL and SHA-1 checksum for this file.");
        }

        URI downloadUri = URI.create(downloadUrl);
        String host = downloadUri.getHost();
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("forgecdn.net") || host.endsWith(".forgecdn.net"))) {
            throw new IOException("The download URL is not a trusted HTTPS Forge CDN URL.");
        }
        validateExtension(project.type(), fileName);

        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Choose an existing Minecraft game directory.");
        Path targetDirectory = root.resolve(folderFor(project.type())).normalize();
        if (!targetDirectory.startsWith(root)) throw new IOException("Invalid installation directory.");
        Files.createDirectories(targetDirectory);
        Path destination = targetDirectory.resolve(uniqueName(targetDirectory, fileName, string(selected, "id"))).normalize();
        if (!destination.startsWith(targetDirectory)) throw new IOException("Invalid target filename.");

        Path temporary = Files.createTempFile(targetDirectory, ".relian-download-", ".part");
        boolean moved = false;
        try {
            HttpRequest downloadRequest = HttpRequest.newBuilder(downloadUri).timeout(Duration.ofMinutes(3)).GET().build();
            HttpResponse<InputStream> response = HTTP.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("CurseForge download failed with HTTP " + response.statusCode() + ".");
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_DOWNLOAD_BYTES) {
                response.body().close();
                throw new IOException("The download exceeds Relian's 300 MB safety limit.");
            }

            MessageDigest digest = sha1();
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) throw new IOException("The download exceeded Relian's 300 MB safety limit.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash)) {
                throw new IOException("SHA-1 verification failed. The file was not installed.");
            }
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

    private static String findSha1(JsonArray hashes) {
        if (hashes == null) return "";
        for (JsonElement element : hashes) {
            if (!element.isJsonObject()) continue;
            JsonObject hash = element.getAsJsonObject();
            if (hash.has("algo") && hash.get("algo").getAsInt() == 1) return string(hash, "value");
        }
        return "";
    }
    private static int loaderType(String loader) throws IOException {
        if (loader == null) throw new IOException("Choose a mod loader.");
        return switch (loader.trim().toLowerCase(Locale.ROOT)) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> throw new IOException("Choose Fabric, Forge, NeoForge, or Quilt for mods.");
        };
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
    private static String uniqueName(Path dir, String name, String fileId) {
        if (!Files.exists(dir.resolve(name))) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        return base + "-" + fileId.replaceAll("[^A-Za-z0-9_-]", "") + ext;
    }
    private static String safeFileName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[. ]+$", "").trim();
        return cleaned.equals(".") || cleaned.equals("..") ? "" : cleaned;
    }
    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static MessageDigest sha1() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-1 is unavailable in this Java runtime.", ex); }
    }
}
