package com.relian.launcher.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        JsonObject metadata;
        try {
            metadata = JsonParser.parseString(Files.readString(metadataFile)).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Minecraft version metadata is invalid.", ex);
        }
        if (!versionId.equals(metadata.has("id") ? metadata.get("id").getAsString() : ""))
            throw new IOException("Installed Minecraft metadata does not match the selected version.");
        String assetIndex = metadata.has("assetIndex") && metadata.get("assetIndex").isJsonObject()
                ? metadata.getAsJsonObject("assetIndex").get("id").getAsString() : "";
        Path libraries = root.resolve("libraries");
        Path assets = root.resolve("assets");
        Path natives = versionDirectory.resolve("natives");
        var spec = new MinecraftLaunchCommandBuilder.LaunchSpec(
                javaExecutable, root, versionDirectory, libraries, assets, natives,
                versionId, assetIndex, metadata.has("mainClass") ? metadata.get("mainClass").getAsString() : "",
                maxMemoryMb, session);
        List<String> command = MinecraftLaunchCommandBuilder.build(metadata, spec);
        if (output != null) output.accept("[Relian] Starting Minecraft " + versionId + "…");
        return processes.start(command, root, output);
    }

    public boolean isRunning() { return processes.isRunning(); }
    public void stop() { processes.stop(); }
    @Override public void close() { processes.close(); }
}
