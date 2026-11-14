package com.relian.launcher.runtime;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeManagerTest {
    @Test
    void selectsExpectedJavaMajorForMinecraftGenerations() {
        assertEquals(8, JavaRuntimeManager.requiredMajor("1.8.9"));
        assertEquals(16, JavaRuntimeManager.requiredMajor("1.17.1"));
        assertEquals(17, JavaRuntimeManager.requiredMajor("1.20.4"));
        assertEquals(21, JavaRuntimeManager.requiredMajor("1.20.5"));
        assertEquals(21, JavaRuntimeManager.requiredMajor("1.21.11"));
        assertEquals(25, JavaRuntimeManager.requiredMajor("26.3"));
    }

    @Test
    void findsOnlyAnExactCompatibleRuntime() {
        var installed = List.of(
            new JavaRuntimeManager.Runtime(Path.of("java-25"), 25, "Java 25"),
            new JavaRuntimeManager.Runtime(Path.of("java-21"), 21, "Java 21"),
            new JavaRuntimeManager.Runtime(Path.of("java-17"), 17, "Java 17")
        );
        assertEquals(Path.of("java-8"), java.util.Optional.ofNullable(
            JavaRuntimeManager.findCompatible(installed, "1.8.9")).map(JavaRuntimeManager.Runtime::executable)
            .orElse(Path.of("java-8")));
        assertEquals(Path.of("java-21"), JavaRuntimeManager.findCompatible(installed, "1.21.11").executable());
        assertEquals(Path.of("java-25"), JavaRuntimeManager.findCompatible(installed, "26.3").executable());
    }
}