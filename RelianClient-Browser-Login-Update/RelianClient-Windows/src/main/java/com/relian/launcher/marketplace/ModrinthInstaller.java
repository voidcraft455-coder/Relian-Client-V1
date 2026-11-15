package com.relian.launcher.marketplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs a selected Modrinth mod/resource pack/shader after checking game compatibility
 * and verifying the downloaded file against the publisher-provided SHA-512 digest.
 */
public final class ModrinthInstaller {
    private static final URI API = URI.create("https://api.modrinth.com/v2");
    private static final long MAX_DOWNLOAD_BYTES = 300L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private ModrinthInstaller() {}

    public static Path install(MarketplaceProject project, String minecraftVersion, String loader, Path gameDirectory)
            throws IOException, InterruptedException {
        List<Path> published = new ArrayList<>();
        try {
            return installInternal(project, minecraftVersion, loader, gameDirectory, new HashSet<>(), "", published);
        } catch (IOException | InterruptedException failure) {
            for (int i = published.size() - 1; i >= 0; i--) {
                try {
                    MarketplaceInstallRegistry.forget(gameDirectory, published.get(i));
                    Files.deleteIfExists(published.get(i));
                } catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw failure;
        }
    }

    private static Path installInternal(MarketplaceProject project, String minecraftVersion, String loader,
                                        Path gameDirectory, Set<String> visited, String pinnedVersionId, List<Path> published)
            throws IOException, InterruptedException {
        if (project == null || gameDirectory == null) throw new IllegalArgumentException("Project and game directory are required.");
        if (!visited.add(project.id())) return null;
        if (!"Modrinth".equalsIgnoreCase(project.source())) throw new IOException("Only Modrinth downloads are currently supported.");
        if (project.type() == ProjectType.MODPACK) {
            throw new IOException("Modpack import is not available yet. Open the project page to review its pack files.");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) throw new IOException("Choose a Minecraft version first.");

        Path root = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Choose an existing Minecraft game directory.");
        String loaderValue = project.type() == ProjectType.MOD ? normalizeLoader(loader) : "";
        JsonObject selectedVersion;
        if (pinnedVersionId != null && !pinnedVersionId.isBlank()) {
            selectedVersion = fetchVersion(pinnedVersionId);
            if (!project.id().equals(string(selectedVersion, "project_id")))
                throw new IOException("Modrinth dependency version does not belong to the expected project.");
            JsonArray supportedGames = selectedVersion.has("game_versions") && selectedVersion.get("game_versions").isJsonArray()
                    ? selectedVersion.getAsJsonArray("game_versions") : new JsonArray();
            boolean gameMatches = false;
            for (JsonElement item : supportedGames) if (minecraftVersion.trim().equals(item.getAsString())) gameMatches = true;
            if (!gameMatches) throw new IOException("A pinned required dependency does not support Minecraft " + minecraftVersion + ".");
            if (project.type() == ProjectType.MOD) {
                JsonArray supportedLoaders = selectedVersion.has("loaders") && selectedVersion.get("loaders").isJsonArray()
                        ? selectedVersion.getAsJsonArray("loaders") : new JsonArray();
                boolean loaderMatches = false;
                for (JsonElement item : supportedLoaders) if (loaderValue.equalsIgnoreCase(item.getAsString())) loaderMatches = true;
                if (!loaderMatches) throw new IOException("A pinned required dependency does not support " + loaderValue + ".");
            }
        } else {
            String versionQuery = "[\"" + jsonEscape(minecraftVersion.trim()) + "\"]";
            String url = API + "/project/" + encodePath(project.id()) + "/version?game_versions=" + enc(versionQuery);
            if (project.type() == ProjectType.MOD) url += "&loaders=" + enc("[\"" + jsonEscape(loaderValue) + "\"]");
            HttpRequest versionsRequest = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
            HttpResponse<String> versionsResponse = HTTP.send(versionsRequest, HttpResponse.BodyHandlers.ofString());
            if (versionsResponse.statusCode() != 200)
                throw new IOException("Modrinth version lookup failed with HTTP " + versionsResponse.statusCode() + ".");
            JsonArray versions;
            try { versions = JsonParser.parseString(versionsResponse.body()).getAsJsonArray(); }
            catch (RuntimeException ex) { throw new IOException("Modrinth returned an invalid version list.", ex); }
            if (versions.isEmpty()) throw new IOException("No compatible files were found for Minecraft " + minecraftVersion + ".");
            selectedVersion = versions.get(0).getAsJsonObject();
        }
        if (project.type() == ProjectType.MOD) {
            for (ModrinthDependencyGraph.Dependency dependency :
                    ModrinthDependencyGraph.requiredDependencies(selectedVersion)) {
                String dependencyProjectId = dependency.projectId();
                if (dependencyProjectId.isBlank() && !dependency.pinnedVersionId().isBlank()) {
                    dependencyProjectId = string(fetchVersion(dependency.pinnedVersionId()), "project_id");
                }
                if (dependencyProjectId.isBlank() || visited.contains(dependencyProjectId)) continue;
                MarketplaceProject dependencyProject = fetchModProject(dependencyProjectId);
                // Install transitive required dependencies before the parent mod, respecting pinned versions.
                installInternal(dependencyProject, minecraftVersion, loader, gameDirectory, visited,
                        dependency.pinnedVersionId(), published);
            }
        }
        String versionId = string(selectedVersion, "id");
        JsonArray files = selectedVersion.getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("The selected Modrinth version has no downloadable files.");

        JsonObject selectedFile = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                selectedFile = file;
                break;
            }
            if (selectedFile == null) selectedFile = file;
        }
        if (selectedFile == null) throw new IOException("No valid downloadable file was provided.");

        String fileName = safeFileName(string(selectedFile, "filename"));
        String downloadUrl = string(selectedFile, "url");
        JsonObject hashes = selectedFile.getAsJsonObject("hashes");
        String expectedHash = hashes == null ? "" : string(hashes, "sha512");
        if (fileName.isBlank() || downloadUrl.isBlank() || expectedHash.isBlank()) {
            throw new IOException("The selected file is missing its name, download URL, or SHA-512 checksum.");
        }

        URI downloadUri = URI.create(downloadUrl);
        String host = downloadUri.getHost();
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("cdn.modrinth.com") || host.endsWith(".modrinth.com"))) {
            throw new IOException("The download URL is not a trusted HTTPS Modrinth CDN URL.");
        }
        validateExtension(project.type(), fileName);

        Path targetDirectory = root.resolve(folderFor(project.type())).normalize();
        if (!targetDirectory.startsWith(root)) throw new IOException("Invalid installation directory.");
        Files.createDirectories(targetDirectory);
        String finalName = uniqueName(targetDirectory, fileName, versionId);
        Path destination = targetDirectory.resolve(finalName).normalize();
        if (!destination.startsWith(targetDirectory)) throw new IOException("Invalid target filename.");

        Path temporary = Files.createTempFile(targetDirectory, ".relian-download-", ".part");
        boolean moved = false;
        try {
            HttpRequest downloadRequest = HttpRequest.newBuilder(downloadUri).timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
            HttpResponse<InputStream> downloadResponse = HTTP.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (downloadResponse.statusCode() != 200) {
                downloadResponse.body().close();
                throw new IOException("Modrinth download failed with HTTP " + downloadResponse.statusCode() + ".");
            }
            String finalHost = downloadResponse.uri().getHost();
            if (finalHost == null || !(finalHost.equalsIgnoreCase("cdn.modrinth.com") || finalHost.endsWith(".modrinth.com"))) {
                downloadResponse.body().close();
                throw new IOException("The final download destination is not a trusted Modrinth CDN host.");
            }
            long declaredLength = downloadResponse.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_DOWNLOAD_BYTES) {
                downloadResponse.body().close();
                throw new IOException("The download exceeds Relian's 300 MB safety limit.");
            }

            MessageDigest digest = sha512();
            long total = 0;
            try (InputStream in = downloadResponse.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) throw new IOException("The download exceeded Relian's 300 MB safety limit.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!actualHash.equalsIgnoreCase(expectedHash)) throw new IOException("SHA-512 verification failed. The file was not installed.");
            if (project.type() == ProjectType.MOD) {
                ContentConflictDetector.ensureNoDuplicateModIds(targetDirectory, temporary);
            }

            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            moved = true;
            published.add(destination);
            MarketplaceInstallRegistry.record(root, destination, "Modrinth", project.id(), versionId,
                    "sha512", expectedHash, project.type(), minecraftVersion.trim(), loaderValue);
            return destination;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject fetchVersion(String versionId) throws IOException, InterruptedException {
        URI uri = URI.create(API + "/version/" + encodePath(versionId));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IOException("Could not retrieve pinned Modrinth dependency version (HTTP " + response.statusCode() + ").");
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Modrinth returned invalid pinned dependency metadata.", ex); }
    }

    private static MarketplaceProject fetchModProject(String projectId) throws IOException, InterruptedException {
        URI uri = URI.create(API + "/project/" + encodePath(projectId));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "RelianClient/0.1.0 (marketplace installer)").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IOException("Could not retrieve required Modrinth dependency " + projectId
                    + " (HTTP " + response.statusCode() + ").");
        JsonObject metadata;
        try { metadata = JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Modrinth returned invalid dependency metadata.", ex); }
        if (!"mod".equalsIgnoreCase(string(metadata, "project_type")))
            throw new IOException("A required dependency is not a supported Modrinth mod: " + projectId);
        String id = string(metadata, "id");
        String slug = string(metadata, "slug");
        if (id.isBlank() || slug.isBlank()) throw new IOException("Modrinth returned incomplete dependency metadata.");
        return new MarketplaceProject(id, fallback(string(metadata, "title"), slug), "Unknown",
                string(metadata, "description"), ProjectType.MOD,
                URI.create("https://modrinth.com/mod/" + slug), java.util.List.of(), "Modrinth");
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String folderFor(ProjectType type) {
        return switch (type) {
            case MOD -> "mods";
            case RESOURCE_PACK -> "resourcepacks";
            case SHADER -> "shaderpacks";
            case MODPACK -> throw new IllegalArgumentException("Modpacks are not installed as single files.");
        };
    }

    private static void validateExtension(ProjectType type, String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean valid = switch (type) {
            case MOD -> lower.endsWith(".jar");
            case RESOURCE_PACK, SHADER -> lower.endsWith(".zip");
            case MODPACK -> false;
        };
        if (!valid) throw new IOException("The downloaded file extension does not match this project type.");
    }

    private static String uniqueName(Path directory, String fileName, String versionId) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) return fileName;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        String suffix = versionId == null || versionId.isBlank() ? "relian" : versionId.replaceAll("[^A-Za-z0-9_-]", "");
        String candidateName = base + "-" + suffix + extension;
        int counter = 2;
        while (Files.exists(directory.resolve(candidateName))) {
            candidateName = base + "-" + suffix + "-" + counter++ + extension;
        }
        return candidateName;
    }

    private static String normalizeLoader(String loader) throws IOException {
        if (loader == null) throw new IOException("Choose a mod loader.");
        return switch (loader.trim().toLowerCase(Locale.ROOT)) {
            case "fabric" -> "fabric";
            case "forge" -> "forge";
            case "neoforge" -> "neoforge";
            case "quilt" -> "quilt";
            default -> throw new IOException("Choose Fabric, Forge, NeoForge, or Quilt for mods.");
        };
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String safeFileName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[. ]+$", "").trim();
        return cleaned.equals(".") || cleaned.equals("..") ? "" : cleaned;
    }
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String jsonEscape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static MessageDigest sha512() throws IOException {
        try { return MessageDigest.getInstance("SHA-512"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-512 is unavailable in this Java runtime.", ex); }
    }
}
