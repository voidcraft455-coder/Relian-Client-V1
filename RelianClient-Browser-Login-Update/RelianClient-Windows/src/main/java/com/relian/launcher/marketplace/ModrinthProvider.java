package com.relian.launcher.marketplace;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Initial Modrinth search adapter. API responses are parsed into a small normalized model. */
public final class ModrinthProvider implements MarketplaceProvider {
    private static final URI SEARCH = URI.create("https://api.modrinth.com/v2/search");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Pattern HIT = Pattern.compile("\\{\\s*\\\"project_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\\"slug\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\\"title\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\".*?\\\"description\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\".*?\\\"author\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\".*?\\\"project_type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?\\}");

    @Override public String providerId() { return "modrinth"; }

    @Override public List<MarketplaceProject> search(String query, ProjectType type, String minecraftVersion, String loader, int limit)
            throws IOException, InterruptedException {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        StringBuilder facets = new StringBuilder("[[\\\"project_type:").append(modrinthType(type)).append("\\\"]");
        if (minecraftVersion != null && !minecraftVersion.isBlank()) facets.append(",[\\\"versions:").append(escapeFacet(minecraftVersion)).append("\\\"]");
        if (loader != null && !loader.isBlank() && !loader.equalsIgnoreCase("all")) facets.append(",[\\\"categories:").append(escapeFacet(loader.toLowerCase())).append("\\\"]");
        facets.append(']');
        String url = SEARCH + "?query=" + enc(query == null ? "" : query) + "&limit=" + safeLimit + "&facets=" + enc(facets.toString());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace integration)").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Modrinth search failed with HTTP " + response.statusCode());
        List<MarketplaceProject> projects = new ArrayList<>();
        Matcher matcher = HIT.matcher(response.body());
        while (matcher.find()) {
            String projectType = matcher.group(6);
            ProjectType normalized = fromModrinthType(projectType);
            if (normalized == null || normalized != type) continue;
            String slug = matcher.group(2);
            projects.add(new MarketplaceProject(matcher.group(1), unescape(matcher.group(3)), unescape(matcher.group(5)),
                    unescape(matcher.group(4)), normalized, URI.create("https://modrinth.com/" + projectType + "/" + slug), List.of(), "Modrinth"));
        }
        return List.copyOf(projects);
    }

    private static String modrinthType(ProjectType type) {
        return switch (type) { case MOD -> "mod"; case MODPACK -> "modpack"; case RESOURCE_PACK -> "resourcepack"; case SHADER -> "shader"; };
    }
    private static ProjectType fromModrinthType(String value) {
        return switch (value) { case "mod" -> ProjectType.MOD; case "modpack" -> ProjectType.MODPACK; case "resourcepack" -> ProjectType.RESOURCE_PACK; case "shader" -> ProjectType.SHADER; default -> null; };
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String escapeFacet(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String unescape(String s) { return s.replace("\\\\\"", "\"").replace("\\\\n", " ").replace("\\\\\\\\", "\\"); }
}