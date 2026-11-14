package com.relian.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.relian.launcher.runtime.MinecraftLaunchCommandBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Exchanges Microsoft OAuth for Xbox Live, XSTS, and Minecraft Services credentials. */
public final class MinecraftAccountAuthenticator {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();

    private MinecraftAccountAuthenticator() {}

    public static MinecraftLaunchCommandBuilder.Session authenticate(String microsoftAccessToken)
            throws IOException, InterruptedException {
        return authenticate(microsoftAccessToken, ignored -> {});
    }

    /** Reports each verification boundary without exposing or logging bearer tokens. */
    public static MinecraftLaunchCommandBuilder.Session authenticate(String microsoftAccessToken,
            Consumer<String> progress) throws IOException, InterruptedException {
        Consumer<String> status = progress == null ? ignored -> {} : progress;
        if (microsoftAccessToken == null || microsoftAccessToken.isBlank())
            throw new IOException("Microsoft sign-in did not provide an access token.");

        status.accept("Checking Xbox Live account access…");
        JsonObject xbox = post("https://user.auth.xboxlive.com/user/authenticate", """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=%s"},
             "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
            """.formatted(escapeJson(microsoftAccessToken)));
        String xboxToken = required(xbox, "Token", "Xbox Live authentication failed.");
        JsonArray claims = array(object(xbox, "DisplayClaims"), "xui");
        String userHash = claims != null && claims.size() > 0 ? string(claims.get(0).getAsJsonObject(), "uhs") : "";
        if (userHash.isBlank()) throw new IOException("Xbox Live did not return a user hash.");

        status.accept("Verifying Xbox security (XSTS)…");
        JsonObject xsts = post("https://xsts.auth.xboxlive.com/xsts/authorize", """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["%s"]},
             "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
            """.formatted(escapeJson(xboxToken)));
        String xstsToken = required(xsts, "Token", "Xbox security authentication failed.");
        JsonArray xui = array(object(xsts, "DisplayClaims"), "xui");
        String xuid = xui != null && xui.size() > 0 ? string(xui.get(0).getAsJsonObject(), "xid") : "";

        status.accept("Requesting a Minecraft Services session…");
        JsonObject minecraft = post("https://api.minecraftservices.com/authentication/login_with_xbox",
                "{\"identityToken\":\"XBL3.0 x=" + escapeJson(userHash) + ";" + escapeJson(xstsToken) + "\"}");
        String minecraftToken = required(minecraft, "access_token", "Minecraft Services rejected the Xbox account.");

        status.accept("Checking Minecraft Java ownership…");
        JsonObject entitlements = get("https://api.minecraftservices.com/entitlements/mcstore", minecraftToken);
        JsonArray items = entitlements.has("items") && entitlements.get("items").isJsonArray()
                ? entitlements.getAsJsonArray("items") : new JsonArray();
        if (items.isEmpty()) throw new IOException("This Microsoft account does not appear to own Minecraft Java Edition.");

        status.accept("Retrieving the Minecraft Java profile…");
        JsonObject profile = get("https://api.minecraftservices.com/minecraft/profile", minecraftToken);
        String name = required(profile, "name", "Minecraft profile lookup failed.");
        String id = required(profile, "id", "Minecraft profile lookup failed.");
        if (!id.matches("(?i)[a-f0-9]{32}")) throw new IOException("Minecraft returned an invalid profile ID.");
        status.accept("Minecraft account verified.");
        return new MinecraftLaunchCommandBuilder.Session(name, id, minecraftToken, xuid);
    }

    private static JsonObject post(String endpoint, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return send(request);
    }

    private static JsonObject get(String endpoint, String bearer) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + bearer).header("Accept", "application/json").GET().build();
        return send(request);
    }

    private static JsonObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "Authentication request failed with HTTP " + response.statusCode() + ".";
            try {
                JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
                if (error.has("XErr")) message += " Xbox error code: " + error.get("XErr").getAsString() + ".";
                else if (error.has("errorMessage")) message += " " + error.get("errorMessage").getAsString();
            } catch (RuntimeException ignoredParse) {}
            throw new IOException(message);
        }
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Authentication service returned invalid JSON.", ex); }
    }

    private static String required(JsonObject object, String key, String message) throws IOException {
        String value = string(object, key);
        if (value.isBlank()) throw new IOException(message);
        return value;
    }
    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
