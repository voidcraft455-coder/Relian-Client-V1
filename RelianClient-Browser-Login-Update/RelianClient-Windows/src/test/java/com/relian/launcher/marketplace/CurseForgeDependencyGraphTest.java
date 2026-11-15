package com.relian.launcher.marketplace;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurseForgeDependencyGraphTest {
    @Test void onlyRequiredDependenciesAreResolvedAndFilePinsAreKept() {
        var json=JsonParser.parseString("""
            [
              {"modId":100,"fileId":200,"relationType":3},
              {"modId":101,"fileId":201,"relationType":2},
              {"modId":102,"fileId":202,"relationType":5},
              {"modId":100,"fileId":999,"relationType":3},
              {"modId":103,"relationType":3}
            ]
            """).getAsJsonArray();
        List<CurseForgeDependencyGraph.Dependency> dependencies=CurseForgeDependencyGraph.requiredDependencies(json);
        assertEquals(2,dependencies.size());
        assertEquals("100",dependencies.get(0).modId());
        assertEquals(200L,dependencies.get(0).fileId());
        assertEquals("103",dependencies.get(1).modId());
        assertNull(dependencies.get(1).fileId());
    }

    @Test void nullAndMalformedEntriesAreIgnored() {
        assertTrue(CurseForgeDependencyGraph.requiredDependencies(null).isEmpty());
        var json=JsonParser.parseString("[null,{}, {\"relationType\":2,\"modId\":1}]").getAsJsonArray();
        assertTrue(CurseForgeDependencyGraph.requiredDependencies(json).isEmpty());
    }
}
