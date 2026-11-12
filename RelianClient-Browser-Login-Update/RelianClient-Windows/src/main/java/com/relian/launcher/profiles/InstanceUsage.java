package com.relian.launcher.profiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Play history used to feature the user's most-played installation on the launcher home page. */
public record InstanceUsage(String profileId, long totalPlaySeconds, Instant lastPlayedAt, boolean pinned) {
    public InstanceUsage {
        profileId = Objects.requireNonNull(profileId, "profileId").trim();
        if (profileId.isEmpty()) throw new IllegalArgumentException("profileId cannot be empty.");
        if (totalPlaySeconds < 0) throw new IllegalArgumentException("Playtime cannot be negative.");
    }

    /** Pinned profiles take precedence; otherwise most-played wins, then most recently played. */
    public static List<InstanceUsage> rankForHome(List<InstanceUsage> instances) {
        return instances.stream()
                .sorted(Comparator.comparing(InstanceUsage::pinned).reversed()
                        .thenComparing(InstanceUsage::totalPlaySeconds, Comparator.reverseOrder())
                        .thenComparing(InstanceUsage::lastPlayedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}