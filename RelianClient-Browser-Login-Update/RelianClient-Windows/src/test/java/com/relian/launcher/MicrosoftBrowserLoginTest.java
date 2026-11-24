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
    @Test
    void acceptsOnlyJavaEditionEntitlements() {
        var javaItems = com.google.gson.JsonParser.parseString(
                "[{\\"name\\":\\"product_minecraft\\"}]").getAsJsonArray();
        var gameItems = com.google.gson.JsonParser.parseString(
                "[{\\"name\\":\\"game_minecraft\\"}]").getAsJsonArray();
        var bedrockOnly = com.google.gson.JsonParser.parseString(
                "[{\\"name\\":\\"product_minecraft_bedrock\\"},{\\"name\\":\\"game_minecraft_bedrock\\"}]").getAsJsonArray();
        var unrelated = com.google.gson.JsonParser.parseString(
                "[{\\"name\\":\\"game_dungeons\\"}]").getAsJsonArray();

        org.junit.jupiter.api.Assertions.assertTrue(MinecraftAccountAuthenticator.hasJavaEditionEntitlement(javaItems));
        org.junit.jupiter.api.Assertions.assertTrue(MinecraftAccountAuthenticator.hasJavaEditionEntitlement(gameItems));
        org.junit.jupiter.api.Assertions.assertFalse(MinecraftAccountAuthenticator.hasJavaEditionEntitlement(bedrockOnly));
        org.junit.jupiter.api.Assertions.assertFalse(MinecraftAccountAuthenticator.hasJavaEditionEntitlement(unrelated));
        org.junit.jupiter.api.Assertions.assertFalse(MinecraftAccountAuthenticator.hasJavaEditionEntitlement(null));
    }

}
