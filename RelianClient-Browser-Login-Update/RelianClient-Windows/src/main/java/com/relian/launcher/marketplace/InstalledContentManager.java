package com.relian.launcher.marketplace;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Safe inventory and reversible enable/disable operations for locally installed content. */
public final class InstalledContentManager {
    public enum Category { ALL, MODS, RESOURCE_PACKS, SHADERS }
    public enum Kind { MOD, RESOURCE_PACK, SHADER }
    public record Item(Path path, Kind kind, boolean enabled, long sizeBytes) {}

    private InstalledContentManager() {}

    public static List<Item> list(Path gameRoot, Category category) throws IOException {
        Path root = checkedRoot(gameRoot);
        List<Item> items = new ArrayList<>();
        if (category == Category.ALL || category == Category.MODS)
            scan(root, "mods", Kind.MOD, ".jar", true, items);
        if (category == Category.ALL || category == Category.RESOURCE_PACKS)
            scan(root, "resourcepacks", Kind.RESOURCE_PACK, ".zip", false, items);
        if (category == Category.ALL || category == Category.SHADERS)
            scan(root, "shaderpacks", Kind.SHADER, ".zip", false, items);
        items.sort(Comparator.comparing(item -> item.path().getFileName().toString().toLowerCase(Locale.ROOT)));
        return List.copyOf(items);
    }

    private static void scan(Path root, String folder, Kind kind, String extension, boolean disabledMods,
                             List<Item> output) throws IOException {
        Path directory = root.resolve(folder);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) continue;
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean enabled = name.endsWith(extension);
                boolean disabled = disabledMods && name.endsWith(extension + ".disabled");
                if (!enabled && !disabled) continue;
                output.add(new Item(file.toAbsolutePath().normalize(), kind, enabled, Files.size(file)));
            }
        }
    }

    /** Rename a mod JAR to .jar.disabled, or restore it. No file contents are changed. */
    public static Path toggleMod(Path gameRoot, Path modFile) throws IOException {
        Path root = checkedRoot(gameRoot);
        Path mods = root.resolve("mods").toRealPath();
        Path source = checkedChild(mods, modFile);
        String name = source.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String targetName;
        if (lower.endsWith(".jar.disabled")) targetName = name.substring(0, name.length() - ".disabled".length());
        else if (lower.endsWith(".jar")) targetName = name + ".disabled";
        else throw new IOException("Only .jar mod files can be enabled or disabled.");
        Path target = mods.resolve(targetName).normalize();
        if (!target.getParent().equals(mods) || Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The destination already exists or is not a safe mod path.");
        try {
            return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            return Files.move(source, target);
        }
    }

    public static void delete(Path gameRoot, Path contentFile) throws IOException {
        Path root = checkedRoot(gameRoot);
        Path normalized = contentFile.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("Invalid content path.");
        String folder = parent.getFileName().toString();
        if (!folder.equals("mods") && !folder.equals("resourcepacks") && !folder.equals("shaderpacks"))
            throw new IOException("The file is outside a supported content folder.");
        Path expectedParent = root.resolve(folder).toRealPath();
        Path source = checkedChild(expectedParent, normalized);
        String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".zip")))
            throw new IOException("Unsupported content file type.");
        Files.delete(source);
    }

    private static Path checkedRoot(Path gameRoot) throws IOException {
        if (gameRoot == null || !Files.isDirectory(gameRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(gameRoot))
            throw new IOException("Choose an existing, non-symbolic-link game directory.");
        return gameRoot.toRealPath();
    }

    private static Path checkedChild(Path expectedParent, Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!expectedParent.equals(normalized.getParent())
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)
                || !normalized.toRealPath().getParent().equals(expectedParent))
            throw new IOException("The selected file is no longer a safe file in the expected content folder.");
        return normalized;
    }
}
