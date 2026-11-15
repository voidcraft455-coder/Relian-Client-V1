package com.relian.launcher.cosmetics;

import com.relian.launcher.runtime.MinecraftLaunchCommandBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftSkinServiceTest {
    @TempDir Path temporary;

    @Test
    void requiresVerifiedMinecraftSession() {
        IOException error = assertThrows(IOException.class,
                () -> MinecraftSkinService.apply(null, temporary.resolve("missing.png"), MinecraftSkinService.Model.CLASSIC));
        assertTrue(error.getMessage().contains("Sign in"));
    }

    @Test
    void rejectsMissingSkinFile() {
        var session = session();
        IOException error = assertThrows(IOException.class,
                () -> MinecraftSkinService.apply(session, temporary.resolve("missing.png"), MinecraftSkinService.Model.CLASSIC));
        assertTrue(error.getMessage().contains("regular skin PNG"));
    }

    @Test
    void rejectsLegacySkinDimensionsForAccountUpload() throws Exception {
        Path legacy = temporary.resolve("legacy.png");
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", legacy.toFile());
        IOException error = assertThrows(IOException.class,
                () -> MinecraftSkinService.apply(session(), legacy, MinecraftSkinService.Model.CLASSIC));
        assertTrue(error.getMessage().contains("64×64"));
    }

    @Test
    void acceptsOnlyPngImageData() throws Exception {
        Path text = temporary.resolve("not-a-skin.png");
        Files.writeString(text, "not a PNG");
        IOException error = assertThrows(IOException.class,
                () -> MinecraftSkinService.apply(session(), text, MinecraftSkinService.Model.CLASSIC));
        assertTrue(error.getMessage().contains("64×64"));
    }

    private static MinecraftLaunchCommandBuilder.Session session() {
        return new MinecraftLaunchCommandBuilder.Session("RelianTest", "0123456789abcdef0123456789abcdef", "test-token", "");
    }
}
