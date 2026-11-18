package com.relian.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Windows-current-user DPAPI storage for the Relian identity provider refresh token. */
public final class RelianAccountVault {
    private static final Gson GSON = new Gson();
    private RelianAccountVault() {}

    private static Path file() throws IOException {
        if (!MicrosoftTokenVault.isSupported()) throw new IOException("Relian account persistence currently requires Windows DPAPI.");
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) throw new IOException("Windows LOCALAPPDATA is unavailable.");
        Path dir = Path.of(local).resolve("Relian").resolve("secure").toAbsolutePath().normalize();
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("Relian secure storage directory is not safe.");
        Files.createDirectories(dir);
        return dir.resolve("relian-account.dpapi");
    }

    public static void save(String email, String refreshToken) throws IOException {
        if (email == null || email.isBlank() || refreshToken == null || refreshToken.isBlank())
            throw new IOException("A verified email and refresh token are required.");
        JsonObject data = new JsonObject();
        data.addProperty("email", email);
        data.addProperty("refreshToken", refreshToken);
        byte[] plain = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = Crypt32Util.cryptProtectData(plain); }
        catch (UnsatisfiedLinkError | RuntimeException ex) { throw new IOException("Windows DPAPI could not protect the Relian account session.", ex); }
        finally { java.util.Arrays.fill(plain, (byte) 0); }
        Path target = file();
        Path temp = Files.createTempFile(target.getParent(), ".relian-account-", ".tmp");
        try {
            Files.write(temp, encrypted);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temp);
            java.util.Arrays.fill(encrypted, (byte) 0);
        }
    }

    public record SavedAccount(String email, String refreshToken) {}

    public static java.util.Optional<SavedAccount> read() throws IOException {
        Path target = file();
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return java.util.Optional.empty();
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.size(target) < 1 || Files.size(target) > 65536)
            throw new IOException("Saved Relian account session is invalid or unsafe.");
        byte[] encrypted = Files.readAllBytes(target);
        try {
            byte[] plain = Crypt32Util.cryptUnprotectData(encrypted);
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
                String email = json.has("email") ? json.get("email").getAsString() : "";
                String refresh = json.has("refreshToken") ? json.get("refreshToken").getAsString() : "";
                if (email.isBlank() || refresh.isBlank()) throw new IOException("Saved Relian account session is incomplete.");
                return java.util.Optional.of(new SavedAccount(email, refresh));
            } finally { java.util.Arrays.fill(plain, (byte) 0); }
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            throw new IOException("Windows could not unlock the saved Relian account session.", ex);
        } finally { java.util.Arrays.fill(encrypted, (byte) 0); }
    }

    public static void clear() throws IOException {
        Path target = file();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Saved Relian account session is not a safe regular file.");
            Files.delete(target);
        }
    }
}
