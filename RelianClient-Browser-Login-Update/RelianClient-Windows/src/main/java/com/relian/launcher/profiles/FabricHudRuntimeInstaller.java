package com.relian.launcher.profiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Installs the Relian-owned Fabric 1.21.1 HUD runtime bundled with the Windows launcher. */
public final class FabricHudRuntimeInstaller {
    public static final String MINECRAFT_VERSION = "1.21.1";
    public static final String FILE_NAME = "relian-client-fabric-1.21.1.jar";
    private static final String RESOURCE = "/client-runtime/RelianClient-Fabric-1.21.1.jar";
    private static final long MAX_BYTES = 128L * 1024L * 1024L;

    private FabricHudRuntimeInstaller() {}

    public static Path installBundled(Path instanceDirectory, String minecraftVersion, String loader)
            throws IOException {
        if (!MINECRAFT_VERSION.equals(minecraftVersion) || !"Fabric".equalsIgnoreCase(loader)) {
            throw new IOException("The bundled Relian HUD runtime requires Minecraft 1.21.1 with Fabric.");
        }
        InputStream resource = FabricHudRuntimeInstaller.class.getResourceAsStream(RESOURCE);
        if (resource == null) {
            throw new IOException("The Relian HUD runtime is not included in this launcher build. Rebuild using the latest successful GitHub Actions workflow.");
        }
        try (resource) {
            return install(instanceDirectory, resource);
        }
    }

    /** Stream overload allows deterministic tests and keeps the archive bytes unmodified. */
    public static Path install(Path instanceDirectory, InputStream runtimeJar) throws IOException {
        if (instanceDirectory == null || runtimeJar == null) throw new IllegalArgumentException("Instance folder and runtime JAR are required.");
        Path root = instanceDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected instance folder is missing or unsafe.");
        }
        Path canonicalRoot = root.toRealPath();
        Path mods = canonicalRoot.resolve("mods");
        if (Files.isSymbolicLink(mods)) throw new IOException("The instance mods folder cannot be a symbolic link.");
        Files.createDirectories(mods);
        if (!Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS) || !mods.toRealPath().getParent().equals(canonicalRoot)) {
            throw new IOException("The instance mods folder resolves outside the selected instance.");
        }

        Path destination = mods.resolve(FILE_NAME);
        if (Files.isSymbolicLink(destination)) throw new IOException("The existing Relian HUD file is a symbolic link.");
        Path temporary = Files.createTempFile(mods, ".relian-hud-", ".part");
        boolean installed = false;
        try {
            long total = 0;
            try (var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = runtimeJar.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new IOException("The Relian HUD runtime exceeds the 128 MB safety limit.");
                    output.write(buffer, 0, read);
                }
            }
            if (total < 4) throw new IOException("The bundled HUD runtime is empty or incomplete.");
            byte[] header = new byte[4];
            try (var input = Files.newInputStream(temporary)) {
                if (input.read(header) != 4 || header[0] != 'P' || header[1] != 'K' || header[2] != 3 || header[3] != 4) {
                    throw new IOException("The bundled HUD runtime is not a valid JAR/ZIP archive.");
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            installed = true;
            return destination;
        } finally {
            if (!installed) Files.deleteIfExists(temporary);
        }
    }
}
