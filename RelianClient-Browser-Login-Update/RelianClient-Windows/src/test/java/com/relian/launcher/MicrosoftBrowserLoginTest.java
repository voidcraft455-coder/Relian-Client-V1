package com.relian.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrosoftBrowserLoginTest {
    @Test
    void rejectsMalformedClientIdBeforeOpeningBrowserOrSendingCredentials() {
        assertThrows(IOException.class, () -> MicrosoftBrowserLogin.signIn("not-a-guid"));
        assertThrows(IOException.class, () -> MicrosoftBrowserLogin.signIn("12345678-1234-1234-1234-not-a-guid"));
    }

    @Test
    void rejectsMissingRefreshTokenBeforeNetworkRequest() {
        assertThrows(IOException.class, () -> MicrosoftBrowserLogin.refresh(
                "12345678-1234-1234-1234-123456789abc", ""));
    }
}
