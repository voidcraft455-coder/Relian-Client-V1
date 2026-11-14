package com.relian.launcher.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Connects an installed vanilla version, a verified Minecraft session, and the process manager. */
public final class MinecraftLaunchService implements AutoCloseable {
    private final MinecraftProcessManager processes = new MinecraftProcessManager();

    public Process launch(Path gameRoot, String versionId, Path javaExecutable,
                          MinecraftLaunchCommandBuilder.Session session, int maxMemoryMb,
                          Consumer<String> output) throws IOException {
        if (versionId == null || !versionId.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IOException("Invalid Minecraft version ID.");
        Path root = gameRoot.toAbsolutePath().normalize();
        Path versionDirectory = root.resolve("versions").resolve(versionId).normalize();
        if (!versionDirectory.startsWith(root.resolve("versions").normalize()))
            throw new IOException("Unsafe Minecraft version directory.");
        Path metadataFile = versionDirectory.resolve(versionId + ".json");
        if (!Files.isRegularFile(metadataFile)) throw new IOException("Install Minecraft " + versionId + " before launching.");
        JsonObject profile;
        try {
            profile = JsonParser.parseString(Files.readString(metadataFile)).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Minecraft version metadata is invalid.", ex);
        }
        if (!versionId.equals(profile.has("id") ? profile.get("id").getAsString() : ""))
            throw new IOException("Installed Minecraft metadata does not match the selected version.");
        ResolvedMetadata resolved = resolveInheritedMetadata(root, profile, 0);
        JsonObject metadata = resolved.metadata();
        Path clientJar = root.resolve("versions").resolve(resolved.baseVersion())
                .resolve(resolved.baseVersion() + ".jar").normalize();
        if (!clientJar.startsWith(root.resolve("versions").normalize()))
            throw new IOException("Unsafe inherited Minecraft client JAR path.");
        metadata.addProperty("relianClientJar", clientJar.toString());
        String assetIndex = metadata.has("assetIndex") && metadata.get("assetIndex").isJsonObject()
                ? metadata.getAsJsonObject("assetIndex").get("id").getAsString() : "";
        Path libraries = root.resolve("libraries");
        Path assets = root.resolve("assets");
        Path natives = root.resolve("versions").resolve(resolved.baseVersion()).resolve("natives");
        var spec = new MinecraftLaunchCommandBuilder.LaunchSpec(
                javaExecutable, root, versionDirectory, libraries, assets, natives,
                versionId, assetIndex, metadata.has("mainClass") ? metadata.get("mainClass").getAsString() : "",
                maxMemoryMb, session);
        List<String> command = MinecraftLaunchCommandBuilder.build(metadata, spec);
        if (output != null) output.accept("[Relian] Starting Minecraft " + versionId + "…");
        return processes.start(command, root, output);
    }

    private record ResolvedMetadata(JsonObject metadata, String baseVersion) {}

    private static ResolvedMetadata resolveInheritedMetadata(Path root, JsonObject child, int depth) throws IOException {
        if (depth > 5) throw new IOException("Minecraft loader metadata has too many inheritance levels.");
        String id = child.has("id") ? child.get("id").getAsString() : "";
        if (!id.matches("[A-Za-z0-9._-]{1,96}")) throw new IOException("Minecraft loader metadata has an invalid version ID.");
        String parentId = child.has("inheritsFrom") ? child.get("inheritsFrom").getAsString() : "";
        if (parentId.isBlank()) return new ResolvedMetadata(child.deepCopy(), id);
        if (!parentId.matches("[A-Za-z0-9._-]{1,96}")) throw new IOException("Minecraft loader metadata has an invalid parent version ID.");
        Path parentFile = root.resolve("versions").resolve(parentId).resolve(parentId + ".json").normalize();
        if (!parentFile.startsWith(root.resolve("versions").normalize()) || !Files.isRegularFile(parentFile))
            throw new IOException("Required parent Minecraft version " + parentId + " is not installed.");
        JsonObject parent;
        try { parent = JsonParser.parseString(Files.readString(parentFile)).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Parent Minecraft metadata is invalid.", ex); }
        ResolvedMetadata inherited = resolveInheritedMetadata(root, parent, depth + 1);
        JsonObject merged = inherited.metadata().deepCopy();
        mergeArrayField(merged, child, "libraries");
        JsonObject parentArguments = merged.has("arguments") && merged.get("arguments").isJsonObject()
                ? merged.getAsJsonObject("arguments") : new JsonObject();
        JsonObject childArguments = child.has("arguments") && child.get("arguments").isJsonObject()
                ? child.getAsJsonObject("arguments") : null;
        if (childArguments != null) {
            JsonObject args = parentArguments.deepCopy();
            for (String key : java.util.List.of("jvm", "game")) {
                JsonArray combined = new JsonArray();
                if (args.has(key) && args.get(key).isJsonArray()) for (JsonElement value : args.getAsJsonArray(key)) combined.add(value.deepCopy());
                if (childArguments.has(key) && childArguments.get(key).isJsonArray()) for (JsonElement value : childArguments.getAsJsonArray(key)) combined.add(value.deepCopy());
                if (!combined.isEmpty()) args.add(key, combined);
            }
            merged.add("arguments", args);
        }
        for (String key : java.util.List.of("id", "mainClass", "logging", "assetIndex", "minecraftArguments", "inheritsFrom")) {
            if (child.has(key)) merged.add(key, child.get(key).deepCopy());
        }
        return new ResolvedMetadata(merged, inherited.baseVersion());
    }

    private static void mergeArrayField(JsonObject target, JsonObject source, String key) {
        JsonArray combined = new JsonArray();
        if (target.has(key) && target.get(key).isJsonArray()) for (JsonElement value : target.getAsJsonArray(key)) combined.add(value.deepCopy());
        if (source.has(key) && source.get(key).isJsonArray()) for (JsonElement value : source.getAsJsonArray(key)) combined.add(value.deepCopy());
        if (!combined.isEmpty()) target.add(key, combined);
    }

    public boolean isRunning() { return processes.isRunning(); }
    public void stop() { processes.stop(); }
    @Override public void close() { processes.close(); }
}
