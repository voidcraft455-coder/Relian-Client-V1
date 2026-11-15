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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/** Downloads and safely imports a marketplace modpack into a new isolated instance. */
public final class MarketplaceModpackInstaller {
    private static final URI MODRINTH = URI.create("https://api.modrinth.com/v2");
    private static final URI CURSEFORGE = URI.create("https://api.curseforge.com/v1");
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private MarketplaceModpackInstaller() {}

    public static InstanceManager.CreatedInstance install(MarketplaceProject project, String minecraftVersion,
            Path instancesRoot, String instanceName, String curseForgeApiKey)
            throws IOException, InterruptedException {
        if (project == null || project.type() != ProjectType.MODPACK)
            throw new IOException("Choose a marketplace modpack.");
        if (minecraftVersion == null || minecraftVersion.isBlank()) throw new IOException("Choose a Minecraft version.");
        if (instancesRoot == null || instanceName == null || instanceName.isBlank())
            throw new IOException("Choose an instances folder and enter an instance name.");

        Path temporaryDirectory = Files.createTempDirectory("relian-marketplace-pack-");
        try {
            Path archive;
            if ("Modrinth".equalsIgnoreCase(project.source())) {
                archive = downloadModrinth(project, minecraftVersion.trim(), temporaryDirectory);
                return ModrinthModpackImporter.importPack(archive, instancesRoot, instanceName);
            }
            if ("CurseForge".equalsIgnoreCase(project.source())) {
                if (curseForgeApiKey == null || curseForgeApiKey.isBlank())
                    throw new IOException("CurseForge modpack installation requires RELIAN_CURSEFORGE_API_KEY.");
                archive = downloadCurseForge(project, minecraftVersion.trim(), temporaryDirectory, curseForgeApiKey.trim());
                return CurseForgeModpackImporter.importPack(archive, instancesRoot, instanceName, curseForgeApiKey);
            }
            throw new IOException("Unsupported modpack marketplace provider.");
        } finally {
            deleteTree(temporaryDirectory);
        }
    }

    private static Path downloadModrinth(MarketplaceProject project, String gameVersion, Path temp)
            throws IOException, InterruptedException {
        String versionsUrl = MODRINTH + "/project/" + encPath(project.id()) + "/version?game_versions="
                + enc("[\"" + escape(gameVersion) + "\"]");
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(versionsUrl))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "RelianClient/0.1.0 (marketplace modpacks)")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Modrinth modpack version lookup failed with HTTP " + response.statusCode() + ".");
        JsonArray versions;
        try { versions = JsonParser.parseString(response.body()).getAsJsonArray(); }
        catch (RuntimeException ex) { throw new IOException("Modrinth returned invalid modpack version metadata.", ex); }
        if (versions.isEmpty()) throw new IOException("No Modrinth modpack version supports Minecraft " + gameVersion + ".");
        JsonObject version = versions.get(0).getAsJsonObject();
        JsonArray files = version.has("files") && version.get("files").isJsonArray() ? version.getAsJsonArray("files") : new JsonArray();
        JsonObject selected = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (string(file, "filename").toLowerCase(Locale.ROOT).endsWith(".mrpack")) {
                if (file.has("primary") && file.get("primary").getAsBoolean()) { selected = file; break; }
                if (selected == null) selected = file;
            }
        }
        if (selected == null) throw new IOException("Modrinth did not provide a downloadable .mrpack archive for this version.");
        return download(string(selected, "url"), string(selected, "filename"),
                string(object(selected, "hashes"), "sha512"), "SHA-512", "cdn.modrinth.com", temp);
    }

    private static Path downloadCurseForge(MarketplaceProject project, String gameVersion, Path temp, String apiKey)
            throws IOException, InterruptedException {
        String url = CURSEFORGE + "/mods/" + encPath(project.id()) + "/files?gameVersion=" + enc(gameVersion)
                + "&pageSize=20&index=0&sortField=2&sortOrder=desc";
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("CurseForge modpack file lookup failed with HTTP " + response.statusCode() + ".");
        JsonArray files;
        try { files = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("data"); }
        catch (RuntimeException ex) { throw new IOException("CurseForge returned invalid modpack file metadata.", ex); }
        if (files == null || files.isEmpty()) throw new IOException("No CurseForge modpack files were found for Minecraft " + gameVersion + ".");
        JsonObject selected = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (file.has("isAvailable") && !file.get("isAvailable").getAsBoolean()) continue;
            if (string(file, "fileName").toLowerCase(Locale.ROOT).endsWith(".zip")) { selected = file; break; }
        }
        if (selected == null) throw new IOException("No downloadable CurseForge modpack ZIP was returned.");
        String sha1 = "";
        JsonArray hashes = selected.has("hashes") && selected.get("hashes").isJsonArray()
                ? selected.getAsJsonArray("hashes") : new JsonArray();
        for (JsonElement hash : hashes) {
            if (hash.isJsonObject() && hash.getAsJsonObject().has("algo")
                    && hash.getAsJsonObject().get("algo").getAsInt() == 1) {
                sha1 = string(hash.getAsJsonObject(), "value"); break;
            }
        }
        return download(string(selected, "downloadUrl"), string(selected, "fileName"),
                sha1, "SHA-1", "forgecdn.net", temp);
    }

    private static Path download(String url, String fileName, String expectedHash, String algorithm,
                                 String allowedHost, Path temp) throws IOException, InterruptedException {
        if (url == null || url.isBlank() || expectedHash == null || expectedHash.isBlank())
            throw new IOException("The marketplace did not provide a direct archive URL and checksum.");
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowed(uri.getHost(), allowedHost))
            throw new IOException("The modpack archive URL is not on an approved HTTPS provider host.");
        String extension = fileName.toLowerCase(Locale.ROOT).endsWith(".mrpack") ? ".mrpack" : ".zip";
        Path archive = Files.createTempFile(temp, "download-", extension);
        boolean complete = false;
        try {
            HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "RelianClient/0.1.0 (marketplace modpacks)").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Modpack archive download failed with HTTP " + response.statusCode() + ".");
            }
            if (!"https".equalsIgnoreCase(response.uri().getScheme()) || !allowed(response.uri().getHost(), allowedHost)) {
                response.body().close();
                throw new IOException("The final modpack download host is not approved.");
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_ARCHIVE_BYTES) {
                response.body().close();
                throw new IOException("The modpack archive exceeds Relian's 512 MB limit.");
            }
            MessageDigest digest = digest(algorithm);
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(archive)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARCHIVE_BYTES) throw new IOException("The modpack archive exceeded Relian's 512 MB limit.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
                throw new IOException(algorithm + " verification failed. The modpack was not imported.");
            complete = true;
            return archive;
        } finally {
            if (!complete) Files.deleteIfExists(archive);
        }
    }

    private static boolean allowed(String host, String allowed) {
        if (host == null) return false;
        return host.equalsIgnoreCase(allowed) || host.toLowerCase(Locale.ROOT).endsWith("." + allowed.toLowerCase(Locale.ROOT));
    }
    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String encPath(String value) { return enc(value).replace("+", "%20"); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static MessageDigest digest(String algorithm) throws IOException {
        try { return MessageDigest.getInstance(algorithm); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("Checksum algorithm is unavailable: " + algorithm, ex); }
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
