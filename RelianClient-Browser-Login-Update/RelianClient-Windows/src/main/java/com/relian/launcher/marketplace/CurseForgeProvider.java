package com.relian.launcher.marketplace;

import java.io.IOException;
import java.util.List;

/** CurseForge API adapter boundary. A valid API key must be supplied by the app operator at runtime. */
public final class CurseForgeProvider implements MarketplaceProvider {
    private final String apiKey;
    public CurseForgeProvider(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }
    @Override public String providerId() { return "curseforge"; }
    public boolean isConfigured() { return !apiKey.isBlank(); }
    @Override public List<MarketplaceProject> search(String query, ProjectType type, String minecraftVersion, String loader, int limit)
            throws IOException {
        if (!isConfigured()) throw new IOException("CurseForge search is not configured. The launcher operator must provide an API key under CurseForge's API terms.");
        throw new IOException("CurseForge API request mapping is the next marketplace implementation step; no fake or scraped results are returned.");
    }
}