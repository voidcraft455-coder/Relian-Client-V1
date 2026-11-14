package com.relian.launcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A local-only identity used to preview launcher account UI without signing in.
 * It is deliberately not a Minecraft authentication session and cannot be
 * passed to the launch service.
 */
public record OfflineTestProfile(String username, String offlineUuid) {
    public OfflineTestProfile {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Use a 3–16 character Minecraft-style name (letters, numbers, underscore).");
        }
        String expected = createOfflineUuid(username);
        if (!expected.equalsIgnoreCase(offlineUuid)) {
            throw new IllegalArgumentException("Offline test profile UUID does not match its local name.");
        }
    }

    public static OfflineTestProfile create(String username) {
        String clean = username == null ? "" : username.trim();
        return new OfflineTestProfile(clean, createOfflineUuid(clean));
    }

    private static String createOfflineUuid(String username) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            String hex = java.util.HexFormat.of().formatHex(digest);
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("This Java runtime does not provide MD5.", impossible);
        }
    }
}
