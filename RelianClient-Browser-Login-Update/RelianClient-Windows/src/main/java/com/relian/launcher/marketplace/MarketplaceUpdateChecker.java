package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Checks tracked marketplace installs against the latest compatible provider versions. */
public final class MarketplaceUpdateChecker {
    public record UpdateCandidate(MarketplaceInstallRegistry.InstallRecord installed,
                                  String latestVersionId, String latestFileName) {}
    private static final URI MODRINTH = URI.create("https://api.modrinth.com/v2");
    private static final URI CURSEFORGE = URI.create("https://api.curseforge.com/v1");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private MarketplaceUpdateChecker() {}

    public static List<UpdateCandidate> check(Path gameRoot, String curseForgeApiKey)
            throws IOException, InterruptedException {
        List<UpdateCandidate> updates = new ArrayList<>();
        for (MarketplaceInstallRegistry.InstallRecord installed : MarketplaceInstallRegistry.list(gameRoot)) {
            Path file = gameRoot.toAbsolutePath().normalize().resolve(installed.relativePath()).normalize();
            if (!file.startsWith(gameRoot.toAbsolutePath().normalize()) || !Files.isRegularFile(file)) continue;
            String latestId;
            String latestName;
            if ("Modrinth".equalsIgnoreCase(installed.source())) {
                JsonArray versions = modrinthVersions(installed);
                if (versions.isEmpty()) continue;
                JsonObject latest = versions.get(0).getAsJsonObject();
                latestId = string(latest, "id");
                latestName = primaryFileName(latest);
            } else if ("CurseForge".equalsIgnoreCase(installed.source())) {
                if (curseForgeApiKey == null || curseForgeApiKey.isBlank())
                    throw new IOException("CurseForge update checks require RELIAN_CURSEFORGE_API_KEY.");
                JsonArray files = curseForgeFiles(installed, curseForgeApiKey.trim());
                JsonObject latest = firstAvailable(files);
                if (latest == null) continue;
                latestId = string(latest, "id");
                latestName = string(latest, "fileName");
            } else continue;
            if (!latestId.isBlank() && !latestId.equals(installed.versionId())) {
                updates.add(new UpdateCandidate(installed, latestId, latestName));
            }
        }
        return List.copyOf(updates);
    }

    private static JsonArray modrinthVersions(MarketplaceInstallRegistry.InstallRecord item)
            throws IOException, InterruptedException {
        String query = "[\"" + escape(item.gameVersion()) + "\"]";
        String url = MODRINTH + "/project/" + encPath(item.projectId()) + "/version?game_versions=" + enc(query);
        if ("MOD".equals(item.projectType()) && item.loader() != null && !item.loader().isBlank())
            url += "&loaders=" + enc("[\"" + escape(item.loader()) + "\"]");
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace updates)").GET().build());
        if (response.statusCode() != 200) throw new IOException("Modrinth update check failed with HTTP " + response.statusCode() + ".");
        try { return JsonParser.parseString(response.body()).getAsJsonArray(); }
        catch (RuntimeException ex) { throw new IOException("Modrinth returned invalid update metadata.", ex); }
    }

    private static JsonArray curseForgeFiles(MarketplaceInstallRegistry.InstallRecord item, String apiKey)
            throws IOException, InterruptedException {
        String url = CURSEFORGE + "/mods/" + encPath(item.projectId()) + "/files?gameVersion="
                + enc(item.gameVersion()) + "&pageSize=20&index=0";
        if ("MOD".equals(item.projectType())) url += "&modLoaderType=" + curseLoaderType(item.loader());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("CurseForge update check failed with HTTP " + response.statusCode() + ".");
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
        } catch (RuntimeException ex) { throw new IOException("CurseForge returned invalid update metadata.", ex); }
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static JsonObject firstAvailable(JsonArray files) {
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (!file.has("isAvailable") || file.get("isAvailable").getAsBoolean()) return file;
        }
        return null;
    }
    private static String primaryFileName(JsonObject version) {
        if (!version.has("files") || !version.get("files").isJsonArray()) return "";
        JsonArray files = version.getAsJsonArray("files");
        JsonObject first = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (first == null) first = file;
            if (file.has("primary") && file.get("primary").getAsBoolean()) return string(file, "filename");
        }
        return first == null ? "" : string(first, "filename");
    }
    private static int curseLoaderType(String loader) throws IOException {
        if (loader == null) throw new IOException("The saved CurseForge install is missing its mod loader.");
        return switch (loader.toLowerCase(java.util.Locale.ROOT)) {
            case "forge" -> 1; case "fabric" -> 4; case "quilt" -> 5; case "neoforge" -> 6;
            default -> throw new IOException("Unsupported saved CurseForge loader: " + loader);
        };
    }
    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String encPath(String value) { return enc(value).replace("+", "%20"); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
