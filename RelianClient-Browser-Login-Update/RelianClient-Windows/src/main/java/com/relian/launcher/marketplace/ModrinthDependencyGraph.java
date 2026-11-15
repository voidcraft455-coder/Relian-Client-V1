package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses Modrinth version dependency declarations into a deterministic install plan.
 * Required project dependencies are installed before the project that declares them.
 * Optional and incompatible dependencies are deliberately not auto-installed.
 */
public final class ModrinthDependencyGraph {
    public record Dependency(String projectId, String pinnedVersionId) {}
    private ModrinthDependencyGraph() {}

    public static List<Dependency> requiredDependencies(String versionJson) {
        if (versionJson == null || versionJson.isBlank()) return List.of();
        JsonObject version = JsonParser.parseString(versionJson).getAsJsonObject();
        return requiredDependencies(version);
    }

    public static List<Dependency> requiredDependencies(JsonObject version) {
        if (version == null || !version.has("dependencies") || !version.get("dependencies").isJsonArray())
            return List.of();
        List<Dependency> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JsonArray dependencies = version.getAsJsonArray("dependencies");
        for (JsonElement element : dependencies) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String type = string(item, "dependency_type");
            if (!"required".equalsIgnoreCase(type)) continue;
            String projectId = string(item, "project_id");
            String versionId = string(item, "version_id");
            // Modrinth may express a dependency as a pinned version only. The caller
            // resolves that version ID to its owning project through /version/{id}.
            String identity = projectId.isBlank() ? "version:" + versionId : "project:" + projectId;
            if ((projectId.isBlank() && versionId.isBlank()) || !seen.add(identity)) continue;
            result.add(new Dependency(projectId, versionId));
        }
        return List.copyOf(result);
    }

    /** Returns dependency declarations in depth-first order while safely stopping cycles. */
    public static List<String> dependencyInstallOrder(String rootProjectId, List<ProjectDependencies> graph) {
        if (rootProjectId == null || rootProjectId.isBlank()) throw new IllegalArgumentException("Root project ID is required.");
        java.util.Map<String, List<String>> edges = new java.util.HashMap<>();
        for (ProjectDependencies item : graph == null ? List.<ProjectDependencies>of() : graph) {
            edges.put(item.projectId(), item.requiredProjectIds());
        }
        List<String> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        visit(rootProjectId, edges, visited, visiting, ordered);
        return List.copyOf(ordered);
    }

    public record ProjectDependencies(String projectId, List<String> requiredProjectIds) {
        public ProjectDependencies {
            requiredProjectIds = requiredProjectIds == null ? List.of() : List.copyOf(requiredProjectIds);
        }
    }

    private static void visit(String id, java.util.Map<String, List<String>> edges, Set<String> visited,
                              Set<String> visiting, List<String> ordered) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) return; // malformed/cyclic provider graph; install each project once
        for (String dependency : edges.getOrDefault(id, List.of())) {
            if (dependency != null && !dependency.isBlank()) visit(dependency, edges, visited, visiting, ordered);
        }
        visiting.remove(id);
        if (visited.add(id)) ordered.add(id);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
