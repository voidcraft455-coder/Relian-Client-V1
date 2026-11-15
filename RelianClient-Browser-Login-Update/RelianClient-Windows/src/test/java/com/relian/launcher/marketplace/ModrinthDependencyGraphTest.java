package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModrinthDependencyGraphTest {
    @Test
    void onlyRequiredDependenciesAreReturnedAndPinnedVersionsArePreserved() {
        String json = """
            {"dependencies":[
              {"project_id":"corelib","version_id":null,"dependency_type":"required"},
              {"project_id":"optional-addon","dependency_type":"optional"},
              {"project_id":"bad-addon","dependency_type":"incompatible"},
              {"project_id":null,"version_id":"v123","dependency_type":"required"},
              {"project_id":"corelib","dependency_type":"required"}
            ]}
            """;
        List<ModrinthDependencyGraph.Dependency> result =
                ModrinthDependencyGraph.requiredDependencies(json);
        assertEquals(2, result.size());
        assertEquals("corelib", result.get(0).projectId());
        assertEquals("v123", result.get(1).pinnedVersionId());
    }

    @Test
    void dependencyOrderPlacesDependenciesBeforeRootAndHandlesCycles() {
        List<ModrinthDependencyGraph.ProjectDependencies> graph = List.of(
                new ModrinthDependencyGraph.ProjectDependencies("root", List.of("library", "helper")),
                new ModrinthDependencyGraph.ProjectDependencies("library", List.of("common")),
                new ModrinthDependencyGraph.ProjectDependencies("helper", List.of("common")),
                new ModrinthDependencyGraph.ProjectDependencies("common", List.of("root"))
        );
        List<String> order = ModrinthDependencyGraph.dependencyInstallOrder("root", graph);
        assertEquals(List.of("root", "library", "common", "helper"), order);
        assertEquals(4, order.stream().distinct().count());
    }

    @Test
    void emptyAndMalformedDependencyArraysDoNotCreateEntries() {
        assertTrue(ModrinthDependencyGraph.requiredDependencies("{}").isEmpty());
        assertTrue(ModrinthDependencyGraph.requiredDependencies(
                "{\"dependencies\":[null,{}, {\"dependency_type\":\"required\"}]}").isEmpty());
    }
}
