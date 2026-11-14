package com.relian.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfflineTestProfileTest {
    @Test
    void createsStableLocalOnlyIdentity() {
        OfflineTestProfile first = OfflineTestProfile.create("RelianTester");
        OfflineTestProfile second = OfflineTestProfile.create("RelianTester");

        assertEquals("RelianTester", first.username());
        assertEquals(first.offlineUuid(), second.offlineUuid());
        assertTrue(first.offlineUuid().matches("(?i)[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"));
    }

    @Test
    void rejectsNamesOutsideMinecraftStyleBounds() {
        assertThrows(IllegalArgumentException.class, () -> OfflineTestProfile.create("ab"));
        assertThrows(IllegalArgumentException.class, () -> OfflineTestProfile.create("name with spaces"));
        assertThrows(IllegalArgumentException.class, () -> OfflineTestProfile.create("this_name_is_way_too_long"));
    }

    @Test
    void rejectsForgedUuid() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfflineTestProfile("RelianTester", "00000000-0000-3000-8000-000000000000"));
    }
}
