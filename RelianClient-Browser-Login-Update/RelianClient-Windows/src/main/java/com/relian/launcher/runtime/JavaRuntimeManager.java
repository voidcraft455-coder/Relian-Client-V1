package com.relian.launcher.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects installed Java executables and selects the Java major version Minecraft expects. */
public final class JavaRuntimeManager {
    private static final Pattern VERSION = Pattern.compile("(?:version|openjdk)\\s+\"?([0-9]+)(?:\\.([0-9]+))?");

    public record Runtime(Path executable, int majorVersion, String versionText) {
        @Override public String toString() {
            return "Java " + majorVersion + " — " + executable;
        }
    }

    private JavaRuntimeManager() {}

    public static List<Runtime> detectInstalled() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        String executable = isWindows() ? "java.exe" : "java";
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) candidates.add(Path.of(javaHome).resolve("bin").resolve(executable));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(Pattern.quote(System.getProperty("path.separator")))) {
                if (!entry.isBlank()) candidates.add(Path.of(entry).resolve(executable));
            }
        }
        if (isWindows()) {
            for (String root : List.of("C:/Program Files/Java", "C:/Program Files/Eclipse Adoptium",
                    "C:/Program Files/Microsoft", "C:/Program Files/Amazon Corretto")) {
                Path directory = Path.of(root);
                if (Files.isDirectory(directory)) {
                    try (var children = Files.list(directory)) {
                        children.forEach(child -> candidates.add(child.resolve("bin").resolve(executable)));
                    } catch (IOException ignored) {}
                }
            }
        }
        List<Runtime> found = new ArrayList<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized)) continue;
            try {
                Process process = new ProcessBuilder(normalized.toString(), "-version")
                        .redirectErrorStream(true).start();
                if (!process.waitFor(4, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    continue;
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                Matcher matcher = VERSION.matcher(output);
                if (!matcher.find()) continue;
                int first = Integer.parseInt(matcher.group(1));
                int second = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                int major = first == 1 ? second : first;
                if (major > 0 && found.stream().noneMatch(item -> item.executable().equals(normalized))) {
                    found.add(new Runtime(normalized, major, output.lines().findFirst().orElse("Java " + major)));
                }
            } catch (IOException | InterruptedException | NumberFormatException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        found.sort((a, b) -> Integer.compare(b.majorVersion(), a.majorVersion()));
        return List.copyOf(found);
    }

    /** Mojang's version requirements: Java 8 for legacy releases, 17 for 1.18–1.20.4, 21 for 1.20.5+. */
    public static int requiredMajor(String minecraftVersion) {
        if (minecraftVersion == null) return 21;
        String[] parts = minecraftVersion.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9].*$", "")) : 0;
            if (major >= 26) return 25;
            if (major > 1 || (major == 1 && (minor > 20 || (minor == 20 && patch >= 5)))) return 21;
            if (major == 1 && minor >= 18) return 17;
            if (major == 1 && minor == 17) return 16;
            return 8;
        } catch (NumberFormatException ex) {
            return 21;
        }
    }

    public static Runtime findCompatible(List<Runtime> installed, String minecraftVersion) {
        int required = requiredMajor(minecraftVersion);
        return installed.stream().filter(runtime -> runtime.majorVersion() == required).findFirst().orElse(null);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
