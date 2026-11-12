package com.relian.launcher.settings;

import java.util.Objects;

/** Persistable background settings for the launcher shell. */
public record BackgroundPreference(Mode mode, String value, double dimOpacity, double blurRadius) {
    public enum Mode { PRESET, SOLID_COLOR, GRADIENT, LOCAL_IMAGE }

    public BackgroundPreference {
        mode = Objects.requireNonNull(mode, "mode");
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("A background value is required.");
        if (dimOpacity < 0 || dimOpacity > 1) throw new IllegalArgumentException("dimOpacity must be between 0 and 1.");
        if (blurRadius < 0 || blurRadius > 40) throw new IllegalArgumentException("blurRadius must be between 0 and 40.");
    }
}