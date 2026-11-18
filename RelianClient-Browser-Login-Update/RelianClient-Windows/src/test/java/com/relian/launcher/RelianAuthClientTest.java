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

}
