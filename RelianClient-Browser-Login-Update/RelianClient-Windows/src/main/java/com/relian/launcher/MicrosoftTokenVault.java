package com.relian.launcher;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Optional;

/**
 * Stores only the Microsoft OAuth refresh token, protected by Windows DPAPI
 * for the current Windows user. No plaintext or cross-user fallback is used.
 */
public final class MicrosoftTokenVault {
    private static final String FILE_NAME = "microsoft-refresh.dpapi";
    private MicrosoftTokenVault() {}

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static Path file() throws IOException {
        if (!isSupported()) throw new IOException("Secure Microsoft token persistence is currently supported on Windows only.");
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isBlank()) throw new IOException("Windows LOCALAPPDATA is unavailable.");
        Path directory = Path.of(appData).resolve("Relian").resolve("secure").toAbsolutePath().normalize();
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("The secure account storage directory is not safe.");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The secure account storage directory changed to an unsafe path.");
        return directory.resolve(FILE_NAME);
    }

    public static void saveRefreshToken(String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.isBlank()) throw new IOException("Microsoft did not provide a refresh token.");
        if (refreshToken.length() > 32768) throw new IOException("Microsoft refresh token exceeds the supported size.");
        Path target = file();
        byte[] plain = refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] protectedBytes;
        try {
            protectedBytes = Crypt32Util.cryptProtectData(plain);
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            throw new IOException("Windows could not protect the Microsoft refresh token with DPAPI.", ex);
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), ".relian-token-", ".tmp");
            Files.write(temporary, protectedBytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
            java.util.Arrays.fill(protectedBytes, (byte) 0);
        }
    }

    public static Optional<String> readRefreshToken() throws IOException {
        Path target = file();
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.size(target) < 1 || Files.size(target) > 65536)
            throw new IOException("The saved Microsoft token file is invalid or unsafe.");
        byte[] encrypted = Files.readAllBytes(target);
        try {
            byte[] plain = Crypt32Util.cryptUnprotectData(encrypted);
            try {
                String token = new String(plain, java.nio.charset.StandardCharsets.UTF_8);
                return token.isBlank() ? Optional.empty() : Optional.of(token);
            } finally {
                java.util.Arrays.fill(plain, (byte) 0);
            }
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            throw new IOException("Windows could not unlock the saved Microsoft token for this user.", ex);
        } finally {
            java.util.Arrays.fill(encrypted, (byte) 0);
        }
    }

    public static void clear() throws IOException {
        Path target = file();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("The saved Microsoft token file is invalid or unsafe.");
            Files.delete(target);
        }
    }
}
