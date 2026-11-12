package com.relian.launcher.cosmetics;

import java.util.Objects;

/** Metadata for a cosmetic entry. Ownership and visibility must be explicit in the UI. */
public record CosmeticItem(String id, String name, Category category, Source source, String previewAsset, boolean owned) {
    public enum Category { SKIN, CAPE, EMOTE, HAT, ACCESSORY, OUTFIT }
    public enum Source { MINECRAFT_ACCOUNT, RELIAN_LOCAL, SERVER_SPECIFIC }

    public CosmeticItem {
        id = Objects.requireNonNull(id, "id").trim();
        name = Objects.requireNonNull(name, "name").trim();
        category = Objects.requireNonNull(category, "category");
        source = Objects.requireNonNull(source, "source");
        previewAsset = previewAsset == null ? "" : previewAsset.trim();
        if (id.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("Cosmetic id and name are required.");
        if (source == Source.MINECRAFT_ACCOUNT && category == Category.EMOTE) {
            throw new IllegalArgumentException("Minecraft account cosmetics do not imply emote support.");
        }
    }

    /** Relian-local cosmetics are previews/local presentation only unless a compatible server explicitly supports them. */
    public boolean isGuaranteedVisibleToOtherPlayers() {
        return source == Source.MINECRAFT_ACCOUNT && category == Category.SKIN;
    }
}