package com.relian.launcher.cosmetics;

import com.relian.launcher.runtime.MinecraftLaunchCommandBuilder;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/** Applies a selected local skin to the signed-in Minecraft Java profile using Mojang's official service. */
public final class MinecraftSkinService {
    public enum Model { CLASSIC("classic"), SLIM("slim");
        private final String apiValue;
        Model(String apiValue) { this.apiValue = apiValue; }
    }

    private static final URI ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();

    private MinecraftSkinService() {}

    public static void apply(MinecraftLaunchCommandBuilder.Session session, Path skin, Model model)
            throws IOException, InterruptedException {
        if (session == null) throw new IOException("Sign in with a verified Microsoft account before applying a skin.");
        if (skin == null || model == null || Files.isSymbolicLink(skin)
                || !Files.isRegularFile(skin, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Choose a regular skin PNG from the Relian wardrobe.");
        }
        long size = Files.size(skin);
        if (size <= 0 || size > MAX_BYTES) throw new IOException("The skin PNG must be between 1 byte and 2 MB.");
        var image = ImageIO.read(skin.toFile());
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
            throw new IOException("Applying an account skin requires a 64×64 PNG. Legacy 64×32 skins can remain in the local library but must be converted first.");
        }

        String boundary = "RelianSkin" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(skin);
        ByteArrayOutputStream body = new ByteArrayOutputStream(fileBytes.length + 512);
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
        write(body, model.apiValue + "\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
        write(body, "Content-Type: image/png\r\n\r\n");
        body.write(fileBytes);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + session.accessToken())
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : response.body().replaceAll("[\\r\\n\\t]", " ").trim();
            if (detail.length() > 240) detail = detail.substring(0, 240);
            throw new IOException("Minecraft skin upload failed with HTTP " + response.statusCode()
                    + (detail.isBlank() ? "." : ": " + detail));
        }
    }

    private static void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
