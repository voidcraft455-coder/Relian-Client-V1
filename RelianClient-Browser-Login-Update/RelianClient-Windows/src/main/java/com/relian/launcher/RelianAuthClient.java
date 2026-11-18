package com.relian.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Supabase Auth REST client. The anon/publishable key is public client configuration, never a service-role key. */
public final class RelianAuthClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();

    public record Config(String projectUrl, String anonKey) {
        public Config {
            if (projectUrl == null || anonKey == null || anonKey.isBlank())
                throw new IllegalArgumentException("Enter the Supabase project URL and public anon/publishable key.");
            try {
                URI uri = URI.create(projectUrl.trim());
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
                boolean trustedProjectHost = host.matches("[a-z0-9-]+\\.supabase\\.co");
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedProjectHost
                        || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                        || uri.getRawQuery() != null || uri.getRawFragment() != null)
                    throw new IllegalArgumentException("Use the HTTPS URL for your Supabase project (for example, https://your-project.supabase.co).");
                projectUrl = "https://" + host;
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("The Supabase project URL is invalid.", ex);
            }
        }
        public URI endpoint(String path) { return URI.create(projectUrl + "/auth/v1/" + path); }
    }

    public record Session(String email, String accessToken, String refreshToken, long expiresAt) {}

    private RelianAuthClient() {}

    public static Session signUp(Config config, String email, String password) throws IOException, InterruptedException {
        validateCredentials(email, password);
        JsonObject response = request(config, "signup", "POST", credentials(email, password), null, false);
        Session session = session(response, email);
        if (session == null) throw new IOException("Registration submitted. Check your email for the verification message, then return to Relian and sign in.");
        return session;
    }

    public static Session signIn(Config config, String email, String password) throws IOException, InterruptedException {
        validateCredentials(email, password);
        JsonObject response = request(config, "token?grant_type=password", "POST", credentials(email, password), null, false);
        Session session = session(response, email);
        if (session == null) throw new IOException("The identity provider did not issue a session. Verify your email address and try again.");
        return session;
    }

    public static Session refresh(Config config, String email, String refreshToken) throws IOException, InterruptedException {
        if (refreshToken == null || refreshToken.isBlank()) throw new IOException("No saved Relian refresh token is available.");
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        Session session = session(request(config, "token?grant_type=refresh_token", "POST", body, null, false), email);
        if (session == null) throw new IOException("Relian identity provider did not refresh the saved session.");
        return session;
    }

    public static void requestPasswordReset(Config config, String email) throws IOException, InterruptedException {
        if (email == null || !email.trim().matches("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"))
            throw new IOException("Enter a valid email address.");
        JsonObject body = new JsonObject();
        body.addProperty("email", email.trim());
        request(config, "recover", "POST", body, null, true);
    }

    public static void signOut(Config config, String accessToken) throws IOException, InterruptedException {
        if (accessToken == null || accessToken.isBlank()) return;
        request(config, "logout", "POST", new JsonObject(), accessToken, true);
    }

    private static JsonObject credentials(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email.trim());
        body.addProperty("password", password);
        return body;
    }

    private static void validateCredentials(String email, String password) throws IOException {
        if (email == null || !email.trim().matches("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"))
            throw new IOException("Enter a valid email address.");
        if (password == null || password.length() < 8 || password.length() > 256)
            throw new IOException("Password must be between 8 and 256 characters.");
    }

    private static JsonObject request(Config config, String path, String method, JsonObject body,
                                      String bearer, boolean allowEmpty) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(config.endpoint(path)).timeout(Duration.ofSeconds(30))
                .header("apikey", config.anonKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (bearer != null && !bearer.isBlank()) builder.header("Authorization", "Bearer " + bearer);
        HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "Identity provider request failed (HTTP " + response.statusCode() + ").";
            try {
                JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
                String detail = first(error, "msg", "message", "error_description", "error");
                if (!detail.isBlank()) message += " " + detail;
                if (response.statusCode() == 400 && (detail.toLowerCase().contains("email not confirmed")
                        || detail.toLowerCase().contains("not confirmed")))
                    message += " Check your inbox for the verification email.";
            } catch (RuntimeException ignored) {}
            throw new IOException(message);
        }
        if (response.body() == null || response.body().isBlank()) {
            if (allowEmpty) return new JsonObject();
            throw new IOException("Identity provider returned an empty response.");
        }
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Identity provider returned an invalid response.", ex); }
    }

    private static Session session(JsonObject json, String fallbackEmail) {
        String access = first(json, "access_token");
        String refresh = first(json, "refresh_token");
        if (access.isBlank() || refresh.isBlank()) return null;
        String email = fallbackEmail;
        if (json.has("user") && json.get("user").isJsonObject()) {
            JsonObject user = json.getAsJsonObject("user");
            String actual = first(user, "email");
            if (!actual.isBlank()) email = actual;
        }
        long expires = System.currentTimeMillis() / 1000L;
        try { expires += json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L; }
        catch (RuntimeException ignored) { expires += 3600L; }
        return new Session(email, access, refresh, expires);
    }

    private static String first(JsonObject json, String... keys) {
        for (String key : keys) if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
            String value = json.get(key).getAsString();
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
