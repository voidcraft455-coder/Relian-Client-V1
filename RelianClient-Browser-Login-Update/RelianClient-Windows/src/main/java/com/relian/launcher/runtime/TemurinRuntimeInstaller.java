package com.relian.launcher.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and installs a checksum-verified Eclipse Temurin JDK for Windows. */
public final class TemurinRuntimeInstaller {
    private static final long MAX_ARCHIVE_BYTES = 400L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 1_500L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private TemurinRuntimeInstaller() {}

    public static Path installWindows(int major, Path runtimeRoot) throws IOException, InterruptedException {
        if (!JavaRuntimeManager.isWindows()) throw new IOException("Automatic Temurin installation currently supports Windows only.");
        if (major < 8 || major > 99) throw new IOException("Unsupported Java major version.");
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("aarch64")
                ? "aarch64" : "x64";
        URI api = URI.create("https://api.adoptium.net/v3/assets/latest/" + major
                + "/hotspot?architecture=" + architecture + "&image_type=jdk&os=windows&vendor=eclipse");
        JsonArray releases = JsonParser.parseString(getText(api, 2L * 1024 * 1024)).getAsJsonArray();
        if (releases.isEmpty()) throw new IOException("Eclipse Temurin has no Windows JDK build for Java " + major + ".");
        JsonObject binary = releases.get(0).getAsJsonObject().getAsJsonObject("binary");
        JsonObject pack = binary == null ? null : binary.getAsJsonObject("package");
        if (pack == null) throw new IOException("Temurin did not provide a downloadable runtime package.");
        String rawLink = pack.has("link") ? pack.get("link").getAsString() : "";
        String checksum = pack.has("checksum") ? pack.get("checksum").getAsString() : "";
        URI download = URI.create(rawLink);
        if (!"https".equalsIgnoreCase(download.getScheme()) || !"github.com".equalsIgnoreCase(download.getHost())
                || !download.getPath().contains("/adoptium/temurin") || !checksum.matches("(?i)[a-f0-9]{64}")) {
            throw new IOException("Temurin returned an untrusted runtime download.");
        }

        Path root = runtimeRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) throw new IOException("The runtime directory cannot be a symbolic link.");
        Files.createDirectories(root);
        Path destination = root.resolve("temurin-" + major + "-" + architecture).normalize();
        if (!destination.startsWith(root) || Files.isSymbolicLink(destination)) throw new IOException("Unsafe runtime destination.");
        Path javaExe = destination.resolve("bin").resolve("java.exe");
        if (Files.isRegularFile(javaExe)) return javaExe;

        Path archive = Files.createTempFile(root, ".temurin-", ".zip");
        Path staging = Files.createTempDirectory(root, ".temurin-stage-");
        boolean installed = false;
        try {
            download(download, archive, checksum);
            long expanded = 0;
            int files = 0;
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                byte[] buffer = new byte[32 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++files > 100_000) throw new IOException("Temurin archive contains too many entries.");
                    String name = entry.getName().replace('\\\\', '/');
                    if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Temurin archive contains an absolute path.");
                    Path output = staging.resolve(name).normalize();
                    if (!output.startsWith(staging)) throw new IOException("Temurin archive contains an unsafe path.");
                    if (entry.isDirectory()) { Files.createDirectories(output); continue; }
                    Files.createDirectories(output.getParent());
                    try (var out = Files.newOutputStream(output)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            expanded += count;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Temurin archive exceeds the extraction limit.");
                            out.write(buffer, 0, count);
                        }
                    }
                }
            }
            Path extractedJava;
            try (var walk = Files.walk(staging, 5)) {
                extractedJava = walk.filter(p -> p.getFileName().toString().equalsIgnoreCase("java.exe")
                        && p.getParent() != null && p.getParent().getFileName().toString().equalsIgnoreCase("bin"))
                        .findFirst().orElseThrow(() -> new IOException("The downloaded Temurin archive has no bin/java.exe."));
            }
            Path extractedHome = extractedJava.getParent().getParent();
            if (Files.exists(destination)) throw new IOException("A partial Java runtime directory already exists; remove it before retrying.");
            try {
                Files.move(extractedHome, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(extractedHome, destination);
            }
            installed = true;
            return javaExe;
        } finally {
            Files.deleteIfExists(archive);
            if (!installed) {
                try (var walk = Files.walk(staging)) {
                    for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
                }
            } else {
                Files.deleteIfExists(staging);
            }
        }
    }

    private static String getText(URI uri, long limit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                .header("User-Agent", "RelianClient/0.1.0").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > limit
                || !"https".equalsIgnoreCase(response.uri().getScheme())
                || !"api.adoptium.net".equalsIgnoreCase(response.uri().getHost()))
            throw new IOException("Temurin runtime metadata request failed or redirected to an untrusted host.");
        return response.body();
    }

    private static void download(URI uri, Path target, String expected) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(8))
                .header("User-Agent", "RelianClient/0.1.0").GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200 || !"https".equalsIgnoreCase(response.uri().getScheme())
                || !( "github.com".equalsIgnoreCase(response.uri().getHost())
                || "release-assets.githubusercontent.com".equalsIgnoreCase(response.uri().getHost())
                || "objects.githubusercontent.com".equalsIgnoreCase(response.uri().getHost()))) {
            response.body().close();
            throw new IOException("Temurin runtime download failed or redirected to an untrusted host.");
        }
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > MAX_ARCHIVE_BYTES) {
            response.body().close();
            throw new IOException("Temurin runtime archive exceeds the download limit.");
        }
        try (InputStream in = response.body(); var out = Files.newOutputStream(target)) {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException ex) { throw new IOException(ex); }
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_ARCHIVE_BYTES) throw new IOException("Temurin runtime archive exceeded the download limit.");
                digest.update(buffer, 0, count);
                out.write(buffer, 0, count);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))
                throw new IOException("Temurin runtime checksum verification failed.");
        }
    }
}
