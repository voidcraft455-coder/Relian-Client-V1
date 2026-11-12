package com.relian.launcher.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Reads Mojang's official version manifest; the manifest, not a hard-coded list, is authoritative. */
public final class MinecraftVersionCatalog {
    public static final URI MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public List<MinecraftRelease> fetchAllReleases() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(MANIFEST_URI).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Mojang version manifest returned HTTP " + response.statusCode());
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray versions = root.getAsJsonArray("versions");
            if (versions == null) throw new IOException("Mojang's version manifest did not contain a versions array.");
            List<MinecraftRelease> releases = new ArrayList<>();
            for (JsonElement element : versions) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = string(item, "id");
                String type = string(item, "type");
                if (!"release".equals(type) || id.isBlank()) continue;
                releases.add(new MinecraftRelease(id, type, string(item, "url"), string(item, "sha1"),
                        item.has("complianceLevel") ? item.get("complianceLevel").getAsInt() : 0));
            }
            if (releases.isEmpty()) throw new IOException("No release entries were found in Mojang's version manifest.");
            return List.copyOf(releases);
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Could not parse Mojang's version manifest.", ex);
        }
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

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
