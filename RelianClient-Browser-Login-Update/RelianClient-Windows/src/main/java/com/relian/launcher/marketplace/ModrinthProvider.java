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

/** Modrinth search adapter using JSON parsing rather than field-order-dependent text matching. */
public final class ModrinthProvider implements MarketplaceProvider {
    private static final URI SEARCH = URI.create("https://api.modrinth.com/v2/search");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Override public String providerId() { return "modrinth"; }

    @Override public List<MarketplaceProject> search(String query, ProjectType type, String minecraftVersion, String loader, int limit)
            throws IOException, InterruptedException {
        if (type == null) throw new IllegalArgumentException("Project type is required.");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        JsonArray facets = new JsonArray();
        addFacet(facets, "project_type:" + modrinthType(type));
        if (minecraftVersion != null && !minecraftVersion.isBlank()) addFacet(facets, "versions:" + minecraftVersion.trim());
        if (type == ProjectType.MOD && loader != null && !loader.isBlank() && !loader.equalsIgnoreCase("all")) {
            addFacet(facets, "categories:" + loader.trim().toLowerCase());
        }

        String url = SEARCH + "?query=" + enc(query == null ? "" : query.trim())
                + "&limit=" + safeLimit + "&facets=" + enc(facets.toString());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace integration)").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Modrinth search failed with HTTP " + response.statusCode());

        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray hits = root.getAsJsonArray("hits");
            if (hits == null) throw new IOException("Modrinth returned a response without a hits array.");
            List<MarketplaceProject> projects = new ArrayList<>();
            for (JsonElement element : hits) {
                if (!element.isJsonObject()) continue;
                JsonObject hit = element.getAsJsonObject();
                String projectType = string(hit, "project_type");
                ProjectType normalized = fromModrinthType(projectType);
                if (normalized == null || normalized != type) continue;
                String id = string(hit, "project_id");
                String slug = string(hit, "slug");
                String title = string(hit, "title");
                if (id.isBlank() || slug.isBlank() || title.isBlank()) continue;
                JsonArray categoryArray = hit.has("categories") && hit.get("categories").isJsonArray()
                        ? hit.getAsJsonArray("categories") : new JsonArray();
                List<String> categories = new ArrayList<>();
                for (JsonElement category : categoryArray) if (category.isJsonPrimitive()) categories.add(category.getAsString());
                projects.add(new MarketplaceProject(id, title, fallback(string(hit, "author"), "Unknown"),
                        string(hit, "description"), normalized,
                        URI.create("https://modrinth.com/" + projectType + "/" + slug), categories, "Modrinth"));
            }
            return List.copyOf(projects);
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Could not parse the Modrinth search response.", ex);
        }
    }

    private static void addFacet(JsonArray facets, String value) {
        JsonArray group = new JsonArray();
        group.add(value);
        facets.add(group);
    }
    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String modrinthType(ProjectType type) {
        return switch (type) { case MOD -> "mod"; case MODPACK -> "modpack"; case RESOURCE_PACK -> "resourcepack"; case SHADER -> "shader"; };
    }
    private static ProjectType fromModrinthType(String value) {
        return switch (value) {
            case "mod" -> ProjectType.MOD;
            case "modpack" -> ProjectType.MODPACK;
            case "resourcepack" -> ProjectType.RESOURCE_PACK;
            case "shader" -> ProjectType.SHADER;
            default -> null;
        };
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
