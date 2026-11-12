package com.relian.launcher.marketplace;

import java.io.IOException;
import java.util.List;

public interface MarketplaceProvider {
    String providerId();
    List<MarketplaceProject> search(String query, ProjectType type, String minecraftVersion, String loader, int limit)
            throws IOException, InterruptedException;
}