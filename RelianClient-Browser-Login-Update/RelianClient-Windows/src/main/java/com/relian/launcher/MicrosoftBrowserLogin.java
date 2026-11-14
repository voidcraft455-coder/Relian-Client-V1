package com.relian.launcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Microsoft OAuth authorization-code flow using the system browser and PKCE.
 * This completes Microsoft identity sign-in only; Xbox Live/XSTS/Minecraft
 * entitlement exchange is a separate required step before game launch.
 */
public final class MicrosoftBrowserLogin {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private MicrosoftBrowserLogin() {}

    public record LoginResult(boolean microsoftAuthenticated, String accessToken) {}

    public static String getClientId() {
        String fromEnv = System.getenv("RELIAN_MS_CLIENT_ID");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        Path config = Path.of("auth.properties");
        if (Files.isRegularFile(config)) {
            Properties p = new Properties();
            try (var in = Files.newInputStream(config)) {
                p.load(in);
                return p.getProperty("microsoft.clientId", "").trim();
            } catch (IOException ignored) { }
        }
        return "";
    }

    public static LoginResult signIn(String clientId) throws Exception {
        if (clientId == null || !clientId.trim().matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            throw new IOException("A valid Microsoft public Application (client) ID is required.");
        }
        clientId = clientId.trim();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("This computer cannot open the default web browser.");
        }
        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(48);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = server.getAddress().getPort();
        String redirectUri = "http://localhost:" + port;
        CountDownLatch callback = new CountDownLatch(1);
        AtomicReference<String> authCode = new AtomicReference<>();
        AtomicReference<String> callbackError = new AtomicReference<>();
        server.createContext("/", exchange -> handleCallback(exchange, state, authCode, callbackError, callback));
        server.start();
        try {
            String url = AUTHORITY + "?client_id=" + enc(clientId)
                    + "&response_type=code&redirect_uri=" + enc(redirectUri)
                    + "&response_mode=query&scope=" + enc("XboxLive.signin offline_access openid profile")
                    + "&prompt=select_account&client_info=1"
                    + "&state=" + enc(state)
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256";
            Desktop.getDesktop().browse(URI.create(url));
            if (!callback.await(4, TimeUnit.MINUTES)) throw new IOException("Timed out waiting for the browser sign-in. Please try again.");
            if (callbackError.get() != null) throw new IOException(callbackError.get());
            String code = authCode.get();
            if (code == null || code.isBlank()) throw new IOException("Microsoft did not return an authorization code.");
            String accessToken = exchangeCode(clientId, code, redirectUri, verifier);
            return new LoginResult(true, accessToken);
        } finally {
            server.stop(0);
        }
    }

    private static void handleCallback(HttpExchange ex, String expectedState,
            AtomicReference<String> code, AtomicReference<String> error, CountDownLatch done) throws IOException {
        try {
            var params = new java.util.HashMap<String, String>();
            String query = ex.getRequestURI().getRawQuery();
            if (query != null) for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
            String returnedState = params.getOrDefault("state", "");
            if (!MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8))) {
                error.set("The sign-in response could not be verified (state mismatch).");
            } else if (params.containsKey("error")) {
                String detail = params.getOrDefault("error_description", params.getOrDefault("error", "unknown error"));
                error.set("Microsoft sign-in error: " + detail);
            } else {
                code.set(params.get("code"));
            }
            byte[] body = ("<!doctype html><html><head><meta charset='utf-8'><title>Relian Client</title></head>"
                    + "<body style='font-family:Segoe UI,Arial;background:#17191e;color:#f2f4f7;padding:48px'>"
                    + "<h2>Relian Client</h2><p>Sign-in response received. You can close this tab and return to Relian.</p></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (var out = ex.getResponseBody()) { out.write(body); }
        } finally { done.countDown(); }
    }

    private static String exchangeCode(String clientId, String code, String redirectUri, String verifier) throws Exception {
        String form = "client_id=" + enc(clientId)
                + "&grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_verifier=" + enc(verifier)
                + "&scope=" + enc("XboxLive.signin offline_access openid profile");
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            String detail = "Check the app registration, public-client setting, XboxLive.signin permission, and localhost redirect.";
            try {
                var error = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                if (error.has("error_description")) detail = error.get("error_description").getAsString();
                else if (error.has("error")) detail = error.get("error").getAsString() + ": " + detail;
            } catch (RuntimeException ignored) { }
            throw new IOException("Microsoft token exchange failed (HTTP " + response.statusCode() + "): " + detail);
        }
        String token = jsonString(response.body(), "access_token");
        if (token == null || token.isBlank()) throw new IOException("Microsoft did not return an access token.");
        return token;
    }

    private static String jsonString(String json, String key) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
    private static String randomUrlSafe(int bytes) { byte[] b = new byte[bytes]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
