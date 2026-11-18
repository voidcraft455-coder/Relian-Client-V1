package com.relian.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelianAuthClientTest {
    @Test
    void acceptsHttpsSupabaseProjectAndNormalizesTrailingSlash() {
        var config = new RelianAuthClient.Config("https://example-project.supabase.co/", "public-anon-key");
        assertEquals("https://example-project.supabase.co", config.projectUrl());
        assertEquals("https://example-project.supabase.co/auth/v1/signup", config.endpoint("signup").toString());
    }

    @Test
    void rejectsNonHttpsOrUntrustedIdentityEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("http://example-project.supabase.co", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://example.com", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("not a url", "public-key"));
    }

    @Test
    void rejectsMissingPublicKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://example-project.supabase.co", ""));
    }
    @Test
    void rejectsSupabaseLookalikeHostsAndUrlDecorations() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://project.supabase.co.evil.example", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://nested.project.supabase.co", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://user@project.supabase.co", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://project.supabase.co/other-path", "public-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new RelianAuthClient.Config("https://project.supabase.co?redirect=evil", "public-key"));
    }

    @Test
    void rejectsInvalidSignInCredentialsBeforeMakingNetworkRequests() {
        var config = new RelianAuthClient.Config("https://example-project.supabase.co", "public-key");
        assertThrows(java.io.IOException.class, () -> RelianAuthClient.signIn(config, "not-an-email", "long-enough-password"));
        assertThrows(java.io.IOException.class, () -> RelianAuthClient.signIn(config, "user@example.com", "short"));
    }

    @Test
    void rejectsInvalidRecoveryCodeBeforeMakingNetworkRequests() {
        var config = new RelianAuthClient.Config("https://example-project.supabase.co", "public-key");
        assertThrows(java.io.IOException.class, () ->
                RelianAuthClient.completePasswordRecovery(config, "user@example.com", "not-a-code", "new-password-123"));
    }

}
