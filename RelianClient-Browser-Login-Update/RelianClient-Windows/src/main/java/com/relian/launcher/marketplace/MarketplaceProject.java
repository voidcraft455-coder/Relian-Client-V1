package com.relian.launcher.marketplace;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record MarketplaceProject(
        String id, String title, String author, String description, ProjectType type,
        URI projectUrl, List<String> categories, String source) {
    public MarketplaceProject {
        id = Objects.requireNonNull(id, "id");
        title = Objects.requireNonNull(title, "title");
        author = author == null ? "Unknown" : author;
        description = description == null ? "" : description;
        type = Objects.requireNonNull(type, "type");
        projectUrl = Objects.requireNonNull(projectUrl, "projectUrl");
        categories = categories == null ? List.of() : List.copyOf(categories);
        source = Objects.requireNonNull(source, "source");
    }
}