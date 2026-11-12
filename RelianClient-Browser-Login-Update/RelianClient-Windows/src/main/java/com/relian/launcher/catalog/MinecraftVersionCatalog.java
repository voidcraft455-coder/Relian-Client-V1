package com.relian.launcher.catalog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Mojang's official version manifest; the manifest, not a hard-coded list, is authoritative. */
public final class MinecraftVersionCatalog {
    public static final URI MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final Pattern ENTRY = Pattern.compile("\\{[^{}]*?\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*?\\\"type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*?\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*?\\\"sha1\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^{}]*?\\\"complianceLevel\\\"\\s*:\\s*(\\d+)[^{}]*?\\}");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public List<MinecraftRelease> fetchAllReleases() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(MANIFEST_URI).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Mojang version manifest returned HTTP " + response.statusCode());
        List<MinecraftRelease> releases = new ArrayList<>();
        Matcher matcher = ENTRY.matcher(response.body());
        while (matcher.find()) {
            MinecraftRelease release = new MinecraftRelease(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), Integer.parseInt(matcher.group(5)));
            if (release.isRelease()) releases.add(release);
        }
        if (releases.isEmpty()) throw new IOException("No release entries could be read from Mojang's version manifest.");
        return List.copyOf(releases);
    }

    /** Includes every official release from 1.19 onward and explicitly preserves the two requested legacy releases. */
    public List<MinecraftRelease> fetchRelianTargets() throws IOException, InterruptedException {
        List<MinecraftRelease> all = fetchAllReleases();
        List<MinecraftRelease> targets = new ArrayList<>();
        for (MinecraftRelease release : all) {
            if (isAtLeast119(release.id()) || release.id().equals("1.8.8") || release.id().equals("1.8.9")) targets.add(release);
        }
        return List.copyOf(targets);
    }

    private boolean isAtLeast119(String id) {
        String[] parts = id.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 1 || (major == 1 && minor >= 19);
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}