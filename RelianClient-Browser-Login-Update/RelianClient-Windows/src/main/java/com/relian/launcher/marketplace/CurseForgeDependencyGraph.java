package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses CurseForge file dependency declarations; only required relationships are automatic. */
public final class CurseForgeDependencyGraph {
    public record Dependency(String modId, Long fileId) {}
    private CurseForgeDependencyGraph() {}

    public static List<Dependency> requiredDependencies(JsonArray dependencies) {
        if (dependencies == null) return List.of();
        List<Dependency> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : dependencies) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("relationType") || item.get("relationType").isJsonNull()) continue;
            int relationType;
            try { relationType = item.get("relationType").getAsInt(); }
            catch (RuntimeException malformed) { continue; }
            if (relationType != 3) continue;
            String modId = string(item, "modId");
            if (modId.isBlank() || !seen.add(modId)) continue;
            Long fileId = item.has("fileId") && !item.get("fileId").isJsonNull()
                    ? item.get("fileId").getAsLong() : null;
            result.add(new Dependency(modId, fileId));
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
