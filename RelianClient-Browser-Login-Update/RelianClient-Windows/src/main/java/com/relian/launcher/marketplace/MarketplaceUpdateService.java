package com.relian.launcher.marketplace;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applies provider updates by installing the verified replacement before retiring the old file. */
public final class MarketplaceUpdateService {
    public record UpdateResult(String previousFile, Path installedFile, Path backupFile) {}
    private MarketplaceUpdateService() {}

    public static List<UpdateResult> updateAll(Path gameRoot, String curseForgeApiKey)
            throws IOException, InterruptedException {
        Path root = checkedRoot(gameRoot);
        List<MarketplaceUpdateChecker.UpdateCandidate> candidates =
                MarketplaceUpdateChecker.check(root, curseForgeApiKey);
        List<UpdateResult> results = new ArrayList<>();
        for (MarketplaceUpdateChecker.UpdateCandidate candidate : candidates) {
            results.add(applyOne(root, candidate, curseForgeApiKey));
        }
        return List.copyOf(results);
    }

    private static UpdateResult applyOne(Path root, MarketplaceUpdateChecker.UpdateCandidate candidate,
                                         String curseForgeApiKey) throws IOException, InterruptedException {
        MarketplaceInstallRegistry.InstallRecord old = candidate.installed();
        Path oldFile = MarketplaceInstallRegistry.resolveFile(root, old);

        ProjectType type;
        try { type = ProjectType.valueOf(old.projectType()); }
        catch (RuntimeException ex) { throw new IOException("Unsupported marketplace content type in install metadata.", ex); }
        URI projectUrl = "Modrinth".equalsIgnoreCase(old.source())
                ? URI.create("https://modrinth.com/" + modrinthType(type) + "/" + old.projectId())
                : URI.create("https://www.curseforge.com/minecraft/" + cursePath(type) + "/" + old.projectId());
        MarketplaceProject project = new MarketplaceProject(old.projectId(), old.fileName(), "Marketplace",
                "Managed marketplace update", type, projectUrl, List.of(), old.source());

        MarketplaceInstallRegistry.forgetRecord(root, old);
        Path inactiveOld = oldFile;
        boolean toggled = false;
        if (isEnabled(oldFile, type)) {
            inactiveOld = InstalledContentManager.toggleContent(root, oldFile);
            toggled = true;
        }
        Path replacement = null;
        try {
            replacement = "Modrinth".equalsIgnoreCase(old.source())
                    ? ModrinthInstaller.install(project, old.gameVersion(), old.loader(), root)
                    : CurseForgeInstaller.install(project, old.gameVersion(), old.loader(), root, curseForgeApiKey);
            Path installedPath = replacement;
            MarketplaceInstallRegistry.InstallRecord installed = MarketplaceInstallRegistry.list(root).stream()
                    .filter(record -> record.relativePath().equals(root.relativize(installedPath).toString().replace('\\', '/')))
                    .findFirst().orElseThrow(() -> new IOException("The replacement installed but its metadata could not be verified."));
            if (!candidate.latestVersionId().equals(installed.versionId())) {
                throw new IOException("The provider returned a different version than the update check. The old file has been kept.");
            }

            Path backupsRoot = root.resolve(".relian-backups");
            if (Files.exists(backupsRoot, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(backupsRoot))
                throw new IOException("Marketplace backup directory cannot be a symbolic link.");
            Files.createDirectories(backupsRoot);
            Path backupDirectory = backupsRoot.resolve("marketplace-updates");
            if (Files.exists(backupDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(backupDirectory))
                throw new IOException("Marketplace update backup directory cannot be a symbolic link.");
            Files.createDirectories(backupDirectory);
            Path backup = backupDirectory.resolve(Instant.now().toEpochMilli() + "-" + safeName(oldFile.getFileName().toString()));
            Files.copy(inactiveOld, backup, StandardCopyOption.COPY_ATTRIBUTES);
            Files.delete(inactiveOld);
            return new UpdateResult(old.fileName(), replacement, backup);
        } catch (IOException | InterruptedException failure) {
            if (replacement != null) {
                try { MarketplaceInstallRegistry.forget(root, replacement); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                try { Files.deleteIfExists(replacement); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            if (toggled && Files.exists(inactiveOld, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Path restored = InstalledContentManager.toggleContent(root, inactiveOld);
                    MarketplaceInstallRegistry.record(root, restored, old.source(), old.projectId(), old.versionId(),
                            old.hashAlgorithm(), old.hash(), type, old.gameVersion(), old.loader());
                } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            } else if (!toggled && Files.exists(oldFile, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    MarketplaceInstallRegistry.record(root, oldFile, old.source(), old.projectId(), old.versionId(),
                            old.hashAlgorithm(), old.hash(), type, old.gameVersion(), old.loader());
                } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static boolean isEnabled(Path file, ProjectType type) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = type == ProjectType.MOD ? ".jar" : ".zip";
        return name.endsWith(ext);
    }
    private static String safeName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String modrinthType(ProjectType type) {
        return switch (type) { case MOD -> "mod"; case MODPACK -> "modpack"; case RESOURCE_PACK -> "resourcepack"; case SHADER -> "shader"; };
    }
    private static String cursePath(ProjectType type) {
        return switch (type) { case MOD -> "mc-mods"; case MODPACK -> "modpacks"; case RESOURCE_PACK -> "texture-packs"; case SHADER -> "shaders"; };
    }
    private static Path checkedRoot(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Choose an existing, non-symbolic-link game directory.");
        return root.toRealPath();
    }
}
