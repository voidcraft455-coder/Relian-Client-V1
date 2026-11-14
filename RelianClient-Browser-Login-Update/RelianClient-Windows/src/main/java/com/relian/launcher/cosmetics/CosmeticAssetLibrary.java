package com.relian.launcher.cosmetics;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local-only wardrobe asset storage. These files are not uploaded or automatically rendered in-game. */
public final class CosmeticAssetLibrary {
    public enum Category {
        SKIN("skins", "Skin", 64, 64, 64, 32),
        CAPE("capes", "Cape", 64, 32, 128, 64),
        WINGS("wings", "Wings", 64, 32, 128, 64),
        HAT("hats", "Hat", 64, 64, 128, 128);

        public final String folder;
        public final String label;
        private final int[][] dimensions;
        Category(String folder, String label, int... dimensions) {
            this.folder = folder; this.label = label;
            this.dimensions = new int[dimensions.length / 2][2];
            for (int i = 0; i < dimensions.length; i += 2) {
                this.dimensions[i / 2][0] = dimensions[i];
                this.dimensions[i / 2][1] = dimensions[i + 1];
            }
        }
        boolean accepts(int width, int height) {
            for (int[] size : dimensions) if (size[0] == width && size[1] == height) return true;
            return false;
        }
        public String acceptedSizes() {
            List<String> values = new ArrayList<>();
            for (int[] size : dimensions) values.add(size[0] + "×" + size[1]);
            return String.join(" or ", values);
        }
    }

    public static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private CosmeticAssetLibrary() {}

    public static Path root() {
        return Path.of(System.getProperty("user.home"), ".relian", "cosmetics");
    }

    public static Path directory(Category category) throws IOException {
        if (category == null) throw new IOException("Choose a cosmetic category.");
        Path base = prepareRoot();
        Path folder = base.resolve(category.folder).normalize();
        if (!folder.getParent().equals(base)) throw new IOException("Invalid cosmetic category folder.");
        if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(folder) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Cosmetic folder is not a safe directory.");
        }
        Files.createDirectories(folder);
        return folder.toRealPath();
    }

    public static Path importPng(Category category, Path source) throws IOException {
        Path folder = directory(category);
        if (source == null || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Choose a regular PNG file.");
        }
        long size = Files.size(source);
        if (size <= 0 || size > MAX_FILE_BYTES) throw new IOException("Cosmetic PNG must be between 1 byte and 2 MB.");
        if (!validPng(category, source)) {
            throw new IOException(category.label + " PNG must be " + category.acceptedSizes() + " pixels.");
        }
        String original = source.getFileName() == null ? category.folder : source.getFileName().toString();
        String base = original.replaceFirst("(?i)\\.png$", "").trim()
                .replaceAll("[^A-Za-z0-9 _.-]", "_").replaceAll("[. ]+$", "");
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = category.folder;
        String name = base + ".png";
        int suffix = 2;
        while (Files.exists(folder.resolve(name), LinkOption.NOFOLLOW_LINKS)) name = base + " (" + suffix++ + ").png";
        Path target = folder.resolve(name).normalize();
        Path temporary = Files.createTempFile(folder, ".relian-cosmetic-", ".part");
        boolean moved = false;
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target); }
            moved = true;
            return target;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public static List<Path> list(Category category) throws IOException {
        Path folder = directory(category);
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.png")) {
            for (Path file : files) {
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try { if (validPng(category, file)) result.add(file); }
                catch (IOException ignored) {}
            }
        }
        result.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }

    public static void delete(Category category, Path file) throws IOException {
        Path folder = directory(category);
        if (file == null || Files.isSymbolicLink(file)) throw new IOException("Invalid cosmetic file.");
        Path candidate = file.toAbsolutePath().normalize();
        if (!folder.equals(candidate.getParent())
                || !candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || !candidate.toRealPath().getParent().equals(folder)) {
            throw new IOException("Only PNG files in this Relian cosmetic folder can be removed.");
        }
        Files.delete(candidate);
    }

    private static boolean validPng(Category category, Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return "png".equalsIgnoreCase(reader.getFormatName())
                        && category.accepts(reader.getWidth(0), reader.getHeight(0));
            } finally { reader.dispose(); }
        }
    }

    private static Path prepareRoot() throws IOException {
        Path root = root().toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Cosmetics library location is not a safe directory.");
        }
        Files.createDirectories(root);
        return root.toRealPath();
    }
}
