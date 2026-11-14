package com.relian.launcher.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the official Mojang metadata-driven JVM/game argument vector. */
public final class MinecraftLaunchCommandBuilder {
    private MinecraftLaunchCommandBuilder() {}

    public record Session(String username, String uuid, String accessToken, String xuid) {
        public Session {
            if (username == null || username.isBlank()) throw new IllegalArgumentException("A Minecraft profile name is required.");
            if (uuid == null || !uuid.replace("-", "").matches("(?i)[a-f0-9]{32}"))
                throw new IllegalArgumentException("A verified Minecraft profile UUID is required.");
            if (accessToken == null || accessToken.isBlank())
                throw new IllegalArgumentException("A valid Minecraft access token is required before launch.");
            xuid = xuid == null ? "" : xuid;
        }
    }

    public record LaunchSpec(Path javaExecutable, Path gameDirectory, Path versionDirectory,
                             Path librariesDirectory, Path assetsDirectory, Path nativesDirectory,
                             String versionId, String assetIndex, String mainClass,
                             int maxMemoryMb, Session session) {}

    public static List<String> build(JsonObject metadata, LaunchSpec spec) throws IOException {
        if (metadata == null || spec == null) throw new IllegalArgumentException("Minecraft metadata and launch settings are required.");
        requireFile(spec.javaExecutable(), "Java executable");
        Path clientJar = metadata.has("relianClientJar") && metadata.get("relianClientJar").isJsonPrimitive()
                ? Path.of(metadata.get("relianClientJar").getAsString())
                : spec.versionDirectory().resolve(spec.versionId() + ".jar");
        requireFile(clientJar, "Minecraft client JAR");
        if (!Files.isDirectory(spec.librariesDirectory())) throw new IOException("Minecraft libraries are not installed.");
        if (!Files.isDirectory(spec.assetsDirectory())) throw new IOException("Minecraft assets are not installed.");
        if (!Files.isDirectory(spec.nativesDirectory())) throw new IOException("Minecraft native libraries are not installed.");

        String mainClass = string(metadata, "mainClass");
        if (mainClass.isBlank()) throw new IOException("Minecraft metadata is missing its main class.");
        String assetIndex = spec.assetIndex() == null || spec.assetIndex().isBlank()
                ? string(object(metadata, "assetIndex"), "id") : spec.assetIndex();

        List<String> classpath = new ArrayList<>();
        JsonArray libraries = array(metadata, "libraries");
        for (JsonElement entry : libraries) {
            if (!entry.isJsonObject()) continue;
            JsonObject library = entry.getAsJsonObject();
            if (!allowedByRules(library)) continue;
            JsonObject downloads = object(library, "downloads");
            JsonObject artifact = object(downloads, "artifact");
            String relative = artifact == null ? mavenArtifactPath(string(library, "name")) : string(artifact, "path");
            if (relative.isBlank()) continue;
            Path jar = spec.librariesDirectory().resolve(relative.replace('\\', '/')).normalize();
            if (!jar.startsWith(spec.librariesDirectory().normalize()))
                throw new IOException("Minecraft metadata contains an unsafe library path.");
            if (!Files.isRegularFile(jar)) throw new IOException("A required Minecraft library is missing: " + relative);
            classpath.add(jar.toString());
        }
        classpath.add(clientJar.toAbsolutePath().normalize().toString());
        String classpathValue = String.join(System.getProperty("path.separator"), classpath);

        String loggingArgument = "";
        JsonObject logging = object(metadata, "logging");
        JsonObject clientLogging = object(logging, "client");
        JsonObject loggingFile = object(clientLogging, "file");
        if (loggingFile != null) {
            String id = string(loggingFile, "id");
            if (!id.isBlank() && id.matches("[A-Za-z0-9._-]{1,96}")) {
                Path config = spec.assetsDirectory().resolve("log_configs").resolve(id).normalize();
                if (config.startsWith(spec.assetsDirectory().normalize()) && Files.isRegularFile(config)) {
                    loggingArgument = "-Dlog4j.configurationFile=" + config;
                }
            }
        }

        List<String> command = new ArrayList<>();
        command.add(spec.javaExecutable().toAbsolutePath().normalize().toString());
        command.add("-Xmx" + Math.max(512, spec.maxMemoryMb()) + "M");
        if (!loggingArgument.isBlank()) command.add(loggingArgument);
        List<String> jvm = argumentValues(metadata, "jvm");
        boolean hasClasspath = false;
        for (String arg : jvm) {
            String expanded = expand(arg, spec, assetIndex, classpathValue);
            if (expanded.equals("-cp") || expanded.equals("-classpath")) hasClasspath = true;
            if (!expanded.isBlank()) command.add(expanded);
        }
        if (!hasClasspath) {
            command.add("-Djava.library.path=" + spec.nativesDirectory().toAbsolutePath().normalize());
            command.add("-cp");
            command.add(classpathValue);
        }
        command.add(mainClass);
        List<String> game = argumentValues(metadata, "game");
        if (game.isEmpty()) {
            String legacy = string(metadata, "minecraftArguments");
            if (legacy.isBlank()) throw new IOException("Minecraft metadata contains no game arguments.");
            game.addAll(splitLegacyArguments(legacy));
        }
        for (String arg : game) {
            String expanded = expand(arg, spec, assetIndex, classpathValue);
            if (!expanded.isBlank()) command.add(expanded);
        }
        return List.copyOf(command);
    }

    private static String mavenArtifactPath(String coordinate) throws IOException {
        if (coordinate == null || coordinate.isBlank()) return "";
        String[] parts = coordinate.split(":");
        if (parts.length < 3 || parts.length > 4) return "";
        for (String part : parts) {
            if (part.isBlank() || part.contains("..") || part.contains("/") || part.contains("\\\\"))
                throw new IOException("Minecraft metadata contains an unsafe Maven library coordinate.");
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1], version = parts[2];
        String classifier = parts.length == 4 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static List<String> argumentValues(JsonObject metadata, String key) throws IOException {
        JsonObject arguments = object(metadata, "arguments");
        JsonArray values = array(arguments, key);
        List<String> result = new ArrayList<>();
        for (JsonElement element : values) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject item = element.getAsJsonObject();
                if (!allowedByRules(item)) continue;
                JsonElement value = item.get("value");
                if (value == null) continue;
                if (value.isJsonArray()) {
                    for (JsonElement part : value.getAsJsonArray()) if (part.isJsonPrimitive()) result.add(part.getAsString());
                } else if (value.isJsonPrimitive()) result.add(value.getAsString());
            }
        }
        return result;
    }

    private static boolean allowedByRules(JsonObject object) {
        JsonArray rules = array(object, "rules");
        if (rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            JsonObject os = object(rule, "os");
            boolean matches = os == null || !os.has("name") || "windows".equals(string(os, "name"));
            if (matches && os != null && os.has("arch")) {
                String arch = string(os, "arch").toLowerCase(Locale.ROOT);
                matches = arch.isBlank() || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains(arch);
            }
            if (matches) allowed = "allow".equals(string(rule, "action"));
        }
        return allowed;
    }

    private static String expand(String value, LaunchSpec spec, String assetIndex, String classpath) {
        String result = value
                .replace("${auth_player_name}", spec.session().username())
                .replace("${version_name}", spec.versionId())
                .replace("${game_directory}", spec.gameDirectory().toAbsolutePath().normalize().toString())
                .replace("${assets_root}", spec.assetsDirectory().toAbsolutePath().normalize().toString())
                .replace("${assets_index_name}", assetIndex)
                .replace("${auth_uuid}", spec.session().uuid().replace("-", ""))
                .replace("${auth_access_token}", spec.session().accessToken())
                .replace("${user_type}", "msa")
                .replace("${version_type}", "release")
                .replace("${user_properties}", "{}")
                .replace("${auth_xuid}", spec.session().xuid())
                .replace("${clientid}", "")
                .replace("${natives_directory}", spec.nativesDirectory().toAbsolutePath().normalize().toString())
                .replace("${launcher_name}", "Relian")
                .replace("${launcher_version}", "0.1.0")
                .replace("${library_directory}", spec.librariesDirectory().toAbsolutePath().normalize().toString())
                .replace("${classpath_separator}", System.getProperty("path.separator"))
                .replace("${classpath}", classpath)
                .replace("${game_assets}", spec.assetsDirectory().resolve("virtual").resolve("legacy").toString());
        return result;
    }

    private static List<String> splitLegacyArguments(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (Character.isWhitespace(c) && !quoted) {
                if (!token.isEmpty()) { result.add(token.toString()); token.setLength(0); }
            } else token.append(c);
        }
        if (!token.isEmpty()) result.add(token.toString());
        return result;
    }

    private static void requireFile(Path file, String label) throws IOException {
        if (file == null || !Files.isRegularFile(file)) throw new IOException(label + " is missing: " + file);
    }
    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }
    private static JsonArray array(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }
    private static String string(JsonObject parent, String key) {
        return parent != null && parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsString() : "";
    }
}
