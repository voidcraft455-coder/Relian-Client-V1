package com.relian.launcher.marketplace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Creates portable snapshots of installed client content and restores them without
 * overwriting existing files. Backups are stored outside the game content folders.
 */
public final class ContentBackupManager {
    private static final Set<String> FOLDERS = Set.of("mods", "resourcepacks", "shaderpacks");
    private static final long MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private ContentBackupManager() {}

    public static Path createBackup(Path gameDirectory) throws IOException {
        Path root = checkedRoot(gameDirectory);
        Path backupDirectory = root.resolve(".relian-backups");
        Files.createDirectories(backupDirectory);
        if (Files.isSymbolicLink(backupDirectory)) throw new IOException("Backup directory cannot be a symbolic link.");
        Path archive = backupDirectory.resolve("content-" + STAMP.format(Instant.now()) + ".zip");
        int suffix = 2;
        while (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
            archive = backupDirectory.resolve("content-" + STAMP.format(Instant.now()) + "-" + suffix++ + ".zip");
        }
        Path temporary = Files.createTempFile(backupDirectory, ".relian-backup-", ".part");
        boolean published = false;
        try {
            try (OutputStream fileOut = Files.newOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(fileOut)) {
                long total = 0;
                for (String folder : FOLDERS) {
                    Path directory = root.resolve(folder);
                    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) continue;
                    try (var stream = Files.newDirectoryStream(directory)) {
                        for (Path item : stream) {
                            if (!Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(item)) continue;
                            String name = item.getFileName().toString();
                            if (!supported(folder, name)) continue;
                            long size = Files.size(item);
                            total += size;
                            if (total > MAX_BACKUP_BYTES) throw new IOException("Content backup exceeds the 2 GB safety limit.");
                            zip.putNextEntry(new ZipEntry(folder + "/" + name));
                            try (InputStream in = Files.newInputStream(item)) { in.transferTo(zip); }
                            zip.closeEntry();
                        }
                    }
                }
            }
            move(temporary, archive);
            published = true;
            return archive;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    /** Restores only files that do not already exist. Existing content is never replaced. */
    public static List<Path> restoreBackup(Path gameDirectory, Path backupArchive) throws IOException {
        Path root = checkedRoot(gameDirectory);
        if (backupArchive == null || !Files.isRegularFile(backupArchive, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(backupArchive)) throw new IOException("Choose a valid backup ZIP file.");
        Path backup = backupArchive.toRealPath();
        Path backupRoot = root.resolve(".relian-backups").toRealPath();
        if (!backup.startsWith(backupRoot) || !backup.getFileName().toString().endsWith(".zip"))
            throw new IOException("The selected backup must be inside this game's .relian-backups folder.");

        Path staging = Files.createTempDirectory(root, ".relian-restore-");
        List<Path> staged = new ArrayList<>();
        List<Path> published = new ArrayList<>();
        try {
            long total = 0;
            int count = 0;
            try (InputStream fileIn = Files.newInputStream(backup); ZipInputStream zip = new ZipInputStream(fileIn)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES) throw new IOException("Backup contains too many entries.");
                    if (entry.isDirectory()) { zip.closeEntry(); continue; }
                    String name = entry.getName().replace('\\', '/');
                    String[] parts = name.split("/", -1);
                    if (parts.length != 2 || !FOLDERS.contains(parts[0]) || !supported(parts[0], parts[1])
                            || parts[1].equals(".") || parts[1].equals("..")) {
                        throw new IOException("Backup contains an unsupported or unsafe path: " + name);
                    }
                    Path target = staging.resolve(parts[0]).resolve(parts[1]).normalize();
                    if (!target.startsWith(staging) || Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Backup contains a duplicate or unsafe destination.");
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[16 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_BACKUP_BYTES) throw new IOException("Backup expands beyond the 2 GB safety limit.");
                            out.write(buffer, 0, read);
                        }
                    }
                    staged.add(target);
                    zip.closeEntry();
                }
            }
            for (Path stagedFile : staged) {
                Path relative = staging.relativize(stagedFile);
                Path destinationDirectory = root.resolve(relative.getName(0).toString());
                Files.createDirectories(destinationDirectory);
                if (Files.isSymbolicLink(destinationDirectory)) throw new IOException("A target content folder is a symbolic link.");
                Path destination = destinationDirectory.resolve(relative.getName(1)).normalize();
                if (!destination.getParent().equals(destinationDirectory) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Restore stopped because a destination already exists: " + destination.getFileName());
            }
            for (Path stagedFile : staged) {
                Path relative = staging.relativize(stagedFile);
                Path destination = root.resolve(relative);
                move(stagedFile, destination);
                published.add(destination);
            }
            return List.copyOf(published);
        } catch (IOException failure) {
            for (Path file : published) {
                try { Files.deleteIfExists(file); } catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally {
            deleteTree(staging);
        }
    }

    public static List<Path> listBackups(Path gameDirectory) throws IOException {
        Path root = checkedRoot(gameDirectory);
        Path directory = root.resolve(".relian-backups");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(p)
                    && p.getFileName().toString().endsWith(".zip"))
                    .sorted((a,b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).toList();
        }
    }

    private static boolean supported(String folder, String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return !name.isBlank() && !name.contains("/") && !name.contains("\\\\")
                && !name.equals(".") && !name.equals("..")
                && (folder.equals("mods") ? lower.endsWith(".jar") || lower.endsWith(".jar.disabled")
                : lower.endsWith(".zip") || lower.endsWith(".zip.disabled"));
    }
    private static Path checkedRoot(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
            throw new IOException("Choose an existing, non-symbolic-link Minecraft game directory.");
        return directory.toRealPath();
    }
    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(source, target); }
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}