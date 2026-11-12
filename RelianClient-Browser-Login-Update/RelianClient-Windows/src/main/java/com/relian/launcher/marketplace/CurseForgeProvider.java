package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** CurseForge Minecraft search adapter. The API key is supplied at runtime and is never stored in source. */
public final class CurseForgeProvider implements MarketplaceProvider {
    private static final URI SEARCH = URI.create("https://api.curseforge.com/v1/mods/search");
    private static final int MINECRAFT_GAME_ID = 432;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private final String apiKey;

    public CurseForgeProvider(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override public String providerId() { return "curseforge"; }
    public boolean isConfigured() { return !apiKey.isBlank(); }

    @Override public List<MarketplaceProject> search(String query, ProjectType type, String minecraftVersion, String loader, int limit)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IOException("CurseForge search is unavailable until an API key is configured by the launcher operator.");
        }
        if (type == null) throw new IllegalArgumentException("Project type is required.");

        int pageSize = Math.max(1, Math.min(limit, 50));
        StringBuilder url = new StringBuilder(SEARCH.toString())
                .append("?gameId=").append(MINECRAFT_GAME_ID)
                .append("&classId=").append(classId(type))
                .append("&pageSize=").append(pageSize)
                .append("&index=0")
                .append("&sortField=2&sortOrder=desc");
        if (query != null && !query.isBlank()) url.append("&searchFilter=").append(enc(query.trim()));
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            url.append("&gameVersion=").append(enc(minecraftVersion.trim()));
        }
        Integer loaderType = loaderType(loader);
        if (type == ProjectType.MOD && loaderType != null && minecraftVersion != null && !minecraftVersion.isBlank()) {
            url.append("&modLoaderType=").append(loaderType);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .header("User-Agent", "RelianClient/0.1.0")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("CurseForge rejected the API key (HTTP " + response.statusCode() + "). Check the key and its API access.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("CurseForge search failed with HTTP " + response.statusCode() + ".");
        }

        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null) throw new IOException("CurseForge returned a response without a data array.");
            List<MarketplaceProject> projects = new ArrayList<>();
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = string(item, "id");
                String name = string(item, "name");
                String slug = string(item, "slug");
                if (id.isBlank() || name.isBlank() || slug.isBlank()) continue;

                String author = "Unknown";
                JsonArray authors = item.has("authors") && item.get("authors").isJsonArray()
                        ? item.getAsJsonArray("authors") : new JsonArray();
                if (!authors.isEmpty() && authors.get(0).isJsonObject()) {
                    author = fallback(string(authors.get(0).getAsJsonObject(), "name"), "Unknown");
                }

                List<String> categories = new ArrayList<>();
                JsonArray categoryArray = item.has("categories") && item.get("categories").isJsonArray()
                        ? item.getAsJsonArray("categories") : new JsonArray();
                for (JsonElement category : categoryArray) {
                    if (category.isJsonObject()) {
                        String categoryName = string(category.getAsJsonObject(), "name");
                        if (!categoryName.isBlank()) categories.add(categoryName);
                    }
                }

                String website = "";
                if (item.has("links") && item.get("links").isJsonObject()) {
                    website = string(item.getAsJsonObject("links"), "websiteUrl");
                }
                URI projectUrl = website.isBlank()
                        ? URI.create("https://www.curseforge.com/minecraft/" + pathSegment(type) + "/" + slug)
                        : URI.create(website);
                projects.add(new MarketplaceProject(id, name, author, string(item, "summary"),
                        type, projectUrl, categories, "CurseForge"));
            }
            return List.copyOf(projects);
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Could not parse the CurseForge search response.", ex);
        }
    }

    private static int classId(ProjectType type) {
        return switch (type) {
            case MOD -> 6;
            case MODPACK -> 4471;
            case RESOURCE_PACK -> 12;
            case SHADER -> 6552;
        };
    }

    private static String pathSegment(ProjectType type) {
        return switch (type) {
            case MOD -> "mc-mods";
            case MODPACK -> "modpacks";
            case RESOURCE_PACK -> "texture-packs";
            case SHADER -> "shaders";
        };
    }

    private static Integer loaderType(String loader) {
        if (loader == null) return null;
        return switch (loader.trim().toLowerCase()) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> null;
        };
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
