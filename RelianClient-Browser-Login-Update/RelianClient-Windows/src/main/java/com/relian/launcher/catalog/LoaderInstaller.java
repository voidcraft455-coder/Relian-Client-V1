package com.relian.launcher.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Installs official Fabric/Quilt profiles or runs the official Forge/NeoForge client installer. */
public final class LoaderInstaller {
    private static final long MAX_PROFILE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_INSTALLER_BYTES = 128L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public record Result(String loader, String minecraftVersion, String loaderVersion,
                         String profileId, Path profileMetadata) {}

    private LoaderInstaller() {}

    public static Result install(String loader, String minecraftVersion, Path gameRoot,
                                 Path javaExecutable, Consumer<String> progress)
            throws IOException, InterruptedException {
        return install(loader, minecraftVersion, "", gameRoot, javaExecutable, progress);
    }

    public static Result install(String loader, String minecraftVersion, String requestedLoaderVersion,
                                 Path gameRoot, Path javaExecutable, Consumer<String> progress)
            throws IOException, InterruptedException {
        String kind = loader == null ? "" : loader.trim();
        String requested = requestedLoaderVersion == null ? "" : requestedLoaderVersion.trim();
        if (!requested.isBlank() && !requested.matches("[0-9A-Za-z._+-]{1,64}"))
            throw new IOException("Invalid requested loader version.");
        if (!java.util.Set.of("Fabric", "Forge", "NeoForge", "Quilt").contains(kind))
            throw new IOException("Choose Fabric, Forge, NeoForge, or Quilt.");
        if (minecraftVersion == null || !minecraftVersion.matches("[0-9A-Za-z._-]{1,32}"))
            throw new IOException("Invalid Minecraft version.");
        Path root = gameRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) throw new IOException("The selected instance directory cannot be a symbolic link.");
        Files.createDirectories(root);
        Consumer<String> status = progress == null ? ignored -> {} : progress;
        if (kind.equals("Fabric") || kind.equals("Quilt")) {
            return installProfileLoader(kind, minecraftVersion, requested, root, status);
        }
        if (javaExecutable == null || !Files.isRegularFile(javaExecutable))
            throw new IOException("Install a compatible Java runtime before installing " + kind + ".");
        String loaderVersion = !requested.isBlank() ? requested : (kind.equals("Forge")
                ? latestForge(minecraftVersion, status) : latestNeoForge(minecraftVersion, status));
        boolean neoLegacy = kind.equals("NeoForge") && minecraftVersion.equals("1.20.1");
        String installerUrl = kind.equals("Forge")
                ? "https://maven.minecraftforge.net/net/minecraftforge/forge/" + minecraftVersion + "-" + loaderVersion
                    + "/forge-" + minecraftVersion + "-" + loaderVersion + "-installer.jar"
                : neoLegacy
                    ? "https://maven.neoforged.net/releases/net/neoforged/forge/" + loaderVersion
                        + "/forge-" + loaderVersion + "-installer.jar"
                    : "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + loaderVersion
                        + "/neoforge-" + loaderVersion + "-installer.jar";
        String fileName = kind.toLowerCase(Locale.ROOT) + "-" + minecraftVersion + "-" + loaderVersion + "-installer.jar";
        Path installer = root.resolve("relian-downloads").resolve(fileName).normalize();
        if (!installer.startsWith(root)) throw new IOException("Unsafe loader installer path.");
        downloadWithSha1(installerUrl, installer, MAX_INSTALLER_BYTES);
        status.accept("Running the official " + kind + " client installer…");
        Process process = new ProcessBuilder(javaExecutable.toString(), "-jar", installer.toString(),
                "--installClient", root.toString()).directory(root.toFile()).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofPlatform().daemon().start(() -> {
            try (var in = process.getInputStream()) {
                byte[] bytes = new byte[4096]; int count;
                while ((count = in.read(bytes)) != -1) {
                    if (output.length() < 16000) output.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {}
        });
        if (!process.waitFor(8, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException(kind + " installer timed out. No launch profile was selected.");
        }
        reader.join(3000);
        if (process.exitValue() != 0)
            throw new IOException(kind + " installer failed (exit " + process.exitValue() + "): "
                    + output.toString().replaceAll("(?i)(access_token|password)=\\S+", "$1=[redacted]"));
        Result result = findInstalledProfile(kind, minecraftVersion, root, loaderVersion);
        status.accept(kind + " " + result.loaderVersion() + " installed.");
        return result;
    }

    private static Result installProfileLoader(String kind, String mc, String requested, Path root, Consumer<String> status)
            throws IOException, InterruptedException {
        String host = kind.equals("Fabric") ? "meta.fabricmc.net" : "meta.quiltmc.org";
        String listUrl = kind.equals("Fabric")
                ? "https://meta.fabricmc.net/v2/versions/loader/" + mc
                : "https://meta.quiltmc.org/v3/versions/loader/" + mc;
        status.accept("Checking compatible " + kind + " versions for Minecraft " + mc + "…");
        JsonArray versions = getJsonArray(listUrl, host);
        JsonObject selected = null;
        for (var element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            JsonObject loader = object(entry, "loader");
            if (loader == null) continue;
            if (requested.isBlank() && kind.equals("Fabric") && entry.has("stable") && !entry.get("stable").getAsBoolean()) continue;
            if (!requested.isBlank() && !requested.equals(string(loader, "version"))) continue;
            selected = loader;
            break;
        }
        if (selected == null || string(selected, "version").isBlank())
            throw new IOException(requested.isBlank()
                ? "No compatible " + kind + " loader version was found for Minecraft " + mc + "."
                : "The modpack's " + kind + " loader version " + requested + " is not available for Minecraft " + mc + ".");
        String loaderVersion = string(selected, "version");
        String profileUrl = kind.equals("Fabric")
                ? "https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loaderVersion + "/profile/json"
                : "https://meta.quiltmc.org/v3/versions/loader/" + mc + "/" + loaderVersion + "/profile/json";
        JsonObject profile = getJsonObject(profileUrl, host);
        String id = string(profile, "id");
        if (!id.matches("[A-Za-z0-9._-]{1,96}") || !mc.equals(string(profile, "inheritsFrom")))
            throw new IOException(kind + " returned an invalid or mismatched launch profile.");
        Path versionRoot = root.resolve("versions").normalize();
        Path profileDir = versionRoot.resolve(id).normalize();
        if (!profileDir.startsWith(versionRoot) || Files.isSymbolicLink(profileDir))
            throw new IOException("Unsafe " + kind + " profile directory.");
        Files.createDirectories(profileDir);
        Path metadata = profileDir.resolve(id + ".json");
        writeAtomically(metadata, profile.toString().getBytes(StandardCharsets.UTF_8));
        MinecraftDependencyInstaller.install(profile, root, status);
        copyBaseNatives(root, mc, profileDir);
        return new Result(kind, mc, loaderVersion, id, metadata);
    }

    private static String latestForge(String mc, Consumer<String> status) throws IOException, InterruptedException {
        status.accept("Checking official Forge promotions…");
        JsonObject promotions = getJsonObject("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
                "files.minecraftforge.net").getAsJsonObject("promos");
        String version = string(promotions, mc + "-recommended");
        if (version.isBlank()) version = string(promotions, mc + "-latest");
        if (version.isBlank()) throw new IOException("Forge does not publish a compatible recommended/latest build for Minecraft " + mc + ".");
        return version;
    }

    private static String latestNeoForge(String mc, Consumer<String> status) throws IOException, InterruptedException {
        String[] parts = mc.split("\\.");
        if (parts.length < 2) throw new IOException("NeoForge compatibility for Minecraft " + mc + " is unknown.");
        String prefix = parts[0].equals("1") ? parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + "." : mc + ".";
        String xml = getText("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
                "maven.neoforged.net", 4 * 1024 * 1024);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        String latest = "";
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (candidate.startsWith(prefix) && compareVersions(candidate, latest) > 0) latest = candidate;
        }
        if (latest.isBlank()) throw new IOException("No NeoForge build matching Minecraft " + mc + " was found.");
        return latest;
    }

    private static int compareVersions(String a, String b) {
        if (b == null || b.isBlank()) return 1;
        String[] x=a.split("[.-]"), y=b.split("[.-]");
        for(int i=0;i<Math.max(x.length,y.length);i++){
            int xi=i<x.length?parseNumber(x[i]):0, yi=i<y.length?parseNumber(y[i]):0;
            if(xi!=yi)return Integer.compare(xi,yi);
        }
        return 0;
    }
    private static int parseNumber(String value) { try{return Integer.parseInt(value);}catch(NumberFormatException ex){return -1;} }

    private static Result findInstalledProfile(String kind, String mc, Path root, String loaderVersion) throws IOException, InterruptedException {
        Path versions = root.resolve("versions");
        if (!Files.isDirectory(versions)) throw new IOException(kind + " installer did not create a versions directory.");
        try (var children = Files.list(versions)) {
            for (Path dir : children.filter(path -> Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)).toList()) {
                try (var files = Files.list(dir)) {
                    Path json = files.filter(path -> path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)).findFirst().orElse(null);
                    if (json == null) continue;
                    JsonObject profile = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                    String id = string(profile, "id");
                    if (!mc.equals(string(profile, "inheritsFrom")) || !id.equals(dir.getFileName().toString())) continue;
                    String libraries = objectArrayText(profile, "libraries").toLowerCase(Locale.ROOT);
                    boolean correctFamily = kind.equals("Forge") ? libraries.contains("net.minecraftforge:forge")
                            : (mc.equals("1.20.1") ? libraries.contains("net.neoforged:forge")
                            : libraries.contains("net.neoforged:neoforge"));
                    if (!correctFamily) continue;
                    if (!libraries.contains(loaderVersion.toLowerCase(Locale.ROOT))) continue;
                    MinecraftDependencyInstaller.install(profile, root, ignored -> {});
                    copyBaseNatives(root, mc, dir);
                    return new Result(kind, mc, loaderVersion, id, json);
                } catch (RuntimeException ignored) { }
            }
        }
        throw new IOException(kind + " installer completed but Relian could not locate its Minecraft " + mc + " launch profile.");
    }

    private static void copyBaseNatives(Path root, String mc, Path profileDirectory) throws IOException {
        Path source = root.resolve("versions").resolve(mc).resolve("natives");
        Path target = profileDirectory.resolve("natives");
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) return;
        Files.createDirectories(target);
        try (var paths = Files.walk(source)) {
            for (Path file : paths.toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("A native library directory contains a symbolic link.");
                if (Files.isRegularFile(file)) {
                    Path destination = target.resolve(source.relativize(file)).normalize();
                    if (!destination.startsWith(target)) throw new IOException("Unsafe native library path.");
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String objectArrayText(JsonObject parent, String key) {
        return parent.has(key) ? parent.get(key).toString() : "";
    }

    private static JsonArray getJsonArray(String url, String host) throws IOException, InterruptedException {
        String text = getText(url, host, MAX_PROFILE_BYTES);
        try { return JsonParser.parseString(text).getAsJsonArray(); }
        catch (RuntimeException ex) { throw new IOException("Loader metadata service returned invalid JSON.", ex); }
    }
    private static JsonObject getJsonObject(String url, String host) throws IOException, InterruptedException {
        String text = getText(url, host, MAX_PROFILE_BYTES);
        try { return JsonParser.parseString(text).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Loader metadata service returned invalid JSON.", ex); }
    }
    private static String getText(String rawUrl, String host, long limit) throws IOException, InterruptedException {
        URI uri = trustedUri(rawUrl, host);
        HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "RelianClient/0.1.0").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) { response.body().close(); throw new IOException("Loader metadata request failed with HTTP " + response.statusCode() + "."); }
        validate(response.uri(), host);
        try (InputStream in=response.body(); var out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count; long total=0;
            while((count=in.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("Loader metadata exceeded its size limit.");out.write(buffer,0,count);}
            return out.toString(StandardCharsets.UTF_8);
        }
    }
    private static void downloadWithSha1(String rawUrl, Path destination, long limit) throws IOException, InterruptedException {
        URI uri=trustedUri(rawUrl, rawUrl.contains("neoforged")?"maven.neoforged.net":"maven.minecraftforge.net");
        String expected=getText(rawUrl+".sha1", uri.getHost(), 256).trim().split("\\s+")[0];
        if(!expected.matches("(?i)[a-f0-9]{40}"))throw new IOException("Loader installer did not publish a valid SHA-1 checksum.");
        Files.createDirectories(destination.getParent());
        if(Files.isSymbolicLink(destination)||Files.isSymbolicLink(destination.getParent()))throw new IOException("Unsafe loader installer destination.");
        if(Files.isRegularFile(destination)&&expected.equalsIgnoreCase(sha1(destination)))return;
        HttpResponse<InputStream> response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()!=200){response.body().close();throw new IOException("Loader installer download failed with HTTP "+response.statusCode()+".");}
        validate(response.uri(),uri.getHost());
        Path temp=Files.createTempFile(destination.getParent(),".relian-loader-",".part");
        boolean moved=false;
        try(InputStream in=response.body();var out=Files.newOutputStream(temp)){
            byte[] buffer=new byte[32768];int count;long total=0;MessageDigest digest=MessageDigest.getInstance("SHA-1");
            while((count=in.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("Loader installer exceeded its size limit.");digest.update(buffer,0,count);out.write(buffer,0,count);}
            if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))throw new IOException("Loader installer checksum verification failed.");
            try(ZipFile zip=new ZipFile(temp.toFile())){if(zip.getEntry("META-INF/MANIFEST.MF")==null)throw new IOException("Downloaded loader installer is not a valid JAR.");}
            Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);moved=true;
        }catch(java.security.NoSuchAlgorithmException ex){throw new IOException("SHA-1 is unavailable.",ex);}
        finally{if(!moved)Files.deleteIfExists(temp);}
    }
    private static URI trustedUri(String raw, String host) throws IOException {
        try{URI uri=URI.create(raw);validate(uri,host);return uri;}
        catch(IllegalArgumentException ex){throw new IOException("Loader metadata supplied an invalid URL.",ex);}
    }
    private static void validate(URI uri,String host)throws IOException{
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!uri.getHost().equalsIgnoreCase(host)||uri.getUserInfo()!=null)
            throw new IOException("Loader request used an unapproved host or protocol.");
    }
    private static String sha1(Path file)throws IOException{
        try{MessageDigest digest=MessageDigest.getInstance("SHA-1");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException ex){throw new IOException("SHA-1 is unavailable.",ex);}
    }
    private static void writeAtomically(Path path,byte[] bytes)throws IOException{
        Path temp=Files.createTempFile(path.getParent(),".relian-profile-",".part");boolean moved=false;
        try{Files.write(temp,bytes);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);moved=true;}
        finally{if(!moved)Files.deleteIfExists(temp);}
    }
    private static JsonObject object(JsonObject p,String k){return p!=null&&p.has(k)&&p.get(k).isJsonObject()?p.getAsJsonObject(k):null;}
    private static String string(JsonObject p,String k){return p!=null&&p.has(k)&&!p.get(k).isJsonNull()?p.get(k).getAsString():"";}
}
