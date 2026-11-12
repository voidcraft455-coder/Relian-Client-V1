package com.relian.launcher.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Downloads Mojang's version metadata and official client JAR into the standard
 * versions/<id> layout. This is the first verified stage of a full installation;
 * libraries, assets, Java runtimes, authentication and process launch are separate.
 */
public final class MinecraftVersionInstaller {
    private static final long MAX_METADATA_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_CLIENT_BYTES = 300L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MinecraftVersionInstaller() {}

    public record InstallResult(Path versionDirectory, Path metadataFile, Path clientJar,
                                String versionId, long clientBytes) {}

    public static InstallResult install(MinecraftRelease release, Path gameDirectory,
                                        Consumer<String> progress)
            throws IOException, InterruptedException {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Consumer<String> status = progress == null ? ignored -> {} : progress;

        String id = release.id();
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IOException("Mojang supplied an invalid Minecraft version ID.");
        }
        URI metadataUri = trustedUri(release.metadataUrl(),
                "piston-meta.mojang.com", "launchermeta.mojang.com");
        status.accept("Downloading official " + id + " version metadata…");
        byte[] metadataBytes = getBytes(metadataUri, MAX_METADATA_BYTES);
        if (!release.sha1().isBlank() && !sha1(metadataBytes).equalsIgnoreCase(release.sha1())) {
            throw new IOException("The version metadata SHA-1 did not match Mojang's version manifest.");
        }

        JsonObject metadata;
        try {
            metadata = JsonParser.parseString(new String(metadataBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Mojang returned invalid version metadata.", ex);
        }
        if (!id.equals(metadata.has("id") ? metadata.get("id").getAsString() : "")) {
            throw new IOException("The downloaded metadata does not match the selected Minecraft version.");
        }
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        JsonObject client = downloads == null ? null : downloads.getAsJsonObject("client");
        if (client == null) throw new IOException("This Minecraft version has no official client download entry.");
        String clientUrl = string(client, "url");
        String clientSha1 = string(client, "sha1");
        if (clientSha1.isBlank()) throw new IOException("Mojang did not provide a client JAR checksum.");
        URI clientUri = trustedUri(clientUrl, "launcher.mojang.com", "piston-data.mojang.com");

        Path root = gameDirectory.toAbsolutePath().normalize();
        Path versionsRoot = root.resolve("versions").normalize();
        Path versionDirectory = versionsRoot.resolve(id).normalize();
        if (!versionsRoot.startsWith(root) || !versionDirectory.startsWith(versionsRoot)) {
            throw new IOException("Invalid version installation path.");
        }
        Files.createDirectories(root);
        rejectSymlinkIfPresent(versionsRoot);
        Files.createDirectories(versionsRoot);
        rejectSymlinkIfPresent(versionDirectory);
        Files.createDirectories(versionDirectory);
        Path metadataFile = versionDirectory.resolve(id + ".json");
        Path clientJar = versionDirectory.resolve(id + ".jar");
        rejectSymlinkIfPresent(metadataFile);
        rejectSymlinkIfPresent(clientJar);

        status.accept("Downloading and verifying Minecraft " + id + " client JAR…");
        long bytes = downloadVerified(clientUri, clientSha1, clientJar, MAX_CLIENT_BYTES);

        writeAtomically(metadataFile, metadataBytes);
        status.accept("Verified client JAR and saved version metadata.");
        return new InstallResult(versionDirectory, metadataFile, clientJar, id, bytes);
    }

    private static byte[] getBytes(URI uri, long limit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                .header("User-Agent", "RelianClient/0.1.0 (Minecraft installer)")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Mojang metadata request failed with HTTP " + response.statusCode() + ".");
        }
        validateFinalHost(response.uri(), "piston-meta.mojang.com", "launchermeta.mojang.com");
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > limit) {
            response.body().close();
            throw new IOException("Mojang metadata exceeded Relian's download size limit.");
        }
        try (InputStream in = response.body(); var out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Mojang metadata exceeded Relian's download size limit.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static long downloadVerified(URI uri, String expectedSha1, Path destination, long limit)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "RelianClient/0.1.0 (Minecraft installer)")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Minecraft client download failed with HTTP " + response.statusCode() + ".");
        }
        validateFinalHost(response.uri(), "launcher.mojang.com", "piston-data.mojang.com");
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > limit) {
            response.body().close();
            throw new IOException("The Minecraft client JAR exceeds Relian's 300 MB safety limit.");
        }

        Path temporary = Files.createTempFile(destination.getParent(), ".relian-client-", ".part");
        boolean moved = false;
        try {
            MessageDigest digest = newSha1();
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) throw new IOException("The Minecraft client JAR exceeded Relian's 300 MB safety limit.");
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                throw new IOException("Minecraft client JAR SHA-1 verification failed. The file was not installed.");
            }
            moveAtomically(temporary, destination);
            moved = true;
            return total;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static URI trustedUri(String value, String... hosts) throws IOException {
        try {
            URI uri = URI.create(value);
            validateFinalHost(uri, hosts);
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Mojang supplied an invalid download URL.", ex);
        }
    }

    private static void validateFinalHost(URI uri, String... hosts) throws IOException {
        String host = uri.getHost();
        boolean allowed = host != null;
        if (allowed) {
            allowed = false;
            for (String candidate : hosts) if (host.equalsIgnoreCase(candidate)) allowed = true;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowed || uri.getUserInfo() != null) {
            throw new IOException("A Minecraft download URL did not use an approved Mojang HTTPS host.");
        }
    }

    private static void rejectSymlinkIfPresent(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Relian will not write through a symbolic link in the selected version directory.");
        }
        if (Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A version installation path is not a regular file or directory.");
        }
    }

    private static void writeAtomically(Path destination, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".relian-metadata-", ".part");
        boolean moved = false;
        try {
            Files.write(temporary, bytes);
            moveAtomically(temporary, destination);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String sha1(byte[] bytes) throws IOException {
        MessageDigest digest = newSha1();
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static MessageDigest newSha1() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException ex) { throw new IOException("SHA-1 is unavailable in this Java runtime.", ex); }
    }
}
