package com.relian.launcher.marketplace;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Detects duplicate mod identifiers before a marketplace JAR is published. */
public final class ContentConflictDetector {
    private static final Pattern TOML_MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([A-Za-z0-9_.-]+)[\"']");
    private ContentConflictDetector() {}

    public static void ensureNoDuplicateModIds(Path modsDirectory, Path candidateJar) throws IOException {
        if (modsDirectory == null || candidateJar == null) throw new IllegalArgumentException("Mod paths are required.");
        Map<String, Path> existing = new HashMap<>();
        if (Files.isDirectory(modsDirectory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(modsDirectory)) {
            try (var files = Files.list(modsDirectory)) {
                for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                    for (String id : modIds(file)) existing.putIfAbsent(id, file);
                }
            }
        }
        for (String id : modIds(candidateJar)) {
            Path conflict = existing.get(id);
            if (conflict != null) {
                throw new IOException("Mod conflict: identifier '" + id + "' is already provided by "
                        + conflict.getFileName() + ". Disable or remove the existing mod before installing this file.");
            }
        }
    }

    static Set<String> modIds(Path jar) throws IOException {
        Set<String> ids = new java.util.LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                try (var in = zip.getInputStream(fabric)) {
                    JsonObject metadata = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    add(ids, metadata.has("id") ? metadata.get("id").getAsString() : "");
                } catch (RuntimeException ex) {
                    throw new IOException("A mod contains invalid fabric.mod.json metadata: " + jar.getFileName(), ex);
                }
            }
            for (String name : new String[]{"META-INF/mods.toml", "META-INF/neoforge.mods.toml"}) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) continue;
                try (var in = zip.getInputStream(entry)) {
                    String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    Matcher matcher = TOML_MOD_ID.matcher(text);
                    while (matcher.find()) add(ids, matcher.group(1));
                }
            }
        } catch (java.util.zip.ZipException ex) {
            throw new IOException("The downloaded mod is not a valid JAR archive.", ex);
        }
        return Set.copyOf(ids);
    }

    private static void add(Set<String> ids, String id) {
        if (id != null && id.matches("[A-Za-z0-9_.-]{1,128}")) ids.add(id.toLowerCase(Locale.ROOT));
    }
}
