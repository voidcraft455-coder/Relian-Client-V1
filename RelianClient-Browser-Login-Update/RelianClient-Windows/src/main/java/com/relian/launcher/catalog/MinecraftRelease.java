package com.relian.launcher.catalog;

/** A release entry from Mojang's official Java Edition version manifest. */
public record MinecraftRelease(String id, String type, String metadataUrl, String sha1, int complianceLevel) {
    public boolean isRelease() { return "release".equals(type); }
}