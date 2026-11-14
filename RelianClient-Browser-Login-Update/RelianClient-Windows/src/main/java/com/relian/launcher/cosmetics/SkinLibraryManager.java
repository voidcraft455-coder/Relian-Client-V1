package com.relian.launcher.cosmetics;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;

/** Safe local library for Minecraft skin PNGs; importing does not upload or equip a skin. */
public final class SkinLibraryManager {
    public static final long MAX_SKIN_BYTES = 2L * 1024L * 1024L;
    private SkinLibraryManager() {}

    public static Path defaultLibraryRoot() {
        return Path.of(System.getProperty("user.home"), ".relian", "cosmetics", "skins");
    }

    public static Path importSkin(Path libraryRoot, Path source) throws IOException {
        Path root = prepareRoot(libraryRoot);
        if (source == null || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Choose a regular PNG skin file.");
        }
        long size = Files.size(source);
        if (size <= 0 || size > MAX_SKIN_BYTES) throw new IOException("Skin PNG must be between 1 byte and 2 MB.");
        if (!isMinecraftSkinPng(source)) {
            throw new IOException("Minecraft skins must be PNG images sized 64×64 or legacy 64×32 pixels.");
        }
        String original = source.getFileName() == null ? "skin" : source.getFileName().toString();
        String base = original.replaceFirst("(?i)\\.png$", "").trim()
                .replaceAll("[^A-Za-z0-9 _.-]", "_").replaceAll("[. ]+$", "");
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = "skin";
        String fileName = uniqueName(root, base);
        Path destination = root.resolve(fileName).normalize();
        Path temporary = Files.createTempFile(root, ".relian-skin-", ".part");
        boolean moved = false;
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            moved = true;
            return destination;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public static List<Path> list(Path libraryRoot) throws IOException {
        Path root = prepareRoot(libraryRoot);
        List<Path> skins = new ArrayList<>();
        try (var files = Files.newDirectoryStream(root, "*.png")) {
            for (Path file : files) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) continue;
                try {
                    if (isMinecraftSkinPng(file)) skins.add(file);
                } catch (IOException ignored) {}
            }
        }
        skins.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return List.copyOf(skins);
    }

    public static void delete(Path libraryRoot, Path skin) throws IOException {
        Path root = prepareRoot(libraryRoot);
        if (skin == null || Files.isSymbolicLink(skin)) throw new IOException("Invalid skin file.");
        Path candidate = skin.toAbsolutePath().normalize();
        if (!candidate.getParent().equals(root) || !candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || !candidate.toRealPath().getParent().equals(root)) {
            throw new IOException("Only PNG files inside the Relian skin library can be removed.");
        }
        Files.delete(candidate);
    }

    private static boolean isMinecraftSkinPng(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName())) return false;
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width == 64 && (height == 64 || height == 32);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Path prepareRoot(Path libraryRoot) throws IOException {
        if (libraryRoot == null) throw new IOException("A skin library folder is required.");
        Path root = libraryRoot.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("The skin library location is not a safe directory.");
        }
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private static String uniqueName(Path root, String base) {
        String candidate = base + ".png";
        int suffix = 2;
        while (Files.exists(root.resolve(candidate), LinkOption.NOFOLLOW_LINKS)) candidate = base + " (" + suffix++ + ").png";
        return candidate;
    }
}
