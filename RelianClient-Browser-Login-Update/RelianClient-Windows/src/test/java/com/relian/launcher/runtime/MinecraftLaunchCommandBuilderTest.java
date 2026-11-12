package com.relian.launcher.runtime;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftLaunchCommandBuilderTest {
    @TempDir Path temp;

    @Test
    void buildsModernLaunchArgumentsAndRequiresAuthenticatedSession() throws Exception {
        Path java = Files.createFile(temp.resolve("java.exe"));
        Path version = Files.createDirectories(temp.resolve("versions/1.21.1"));
        Files.createFile(version.resolve("1.21.1.jar"));
        Path libraries = Files.createDirectories(temp.resolve("libraries"));
        Path library = Files.createDirectories(libraries.resolve("org/example/demo/1.0"));
        Files.createFile(library.resolve("demo-1.0.jar"));
        Path assets = Files.createDirectories(temp.resolve("assets"));
        Path natives = Files.createDirectories(version.resolve("natives"));
        Path game = Files.createDirectories(temp.resolve("instance"));
        var metadata = JsonParser.parseString("""
            {
              "id":"1.21.1",
              "mainClass":"net.minecraft.client.main.Main",
              "assetIndex":{"id":"17"},
              "libraries":[{"downloads":{"artifact":{"path":"org/example/demo/1.0/demo-1.0.jar"}}}],
              "arguments":{
                "jvm":["-Djava.library.path=${natives_directory}","-cp","${classpath}"],
                "game":["--username","${auth_player_name}","--uuid","${auth_uuid}","--accessToken","${auth_access_token}","--gameDir","${game_directory}","--assetsDir","${assets_root}","--assetIndex","${assets_index_name}"]
              }
            }
            """).getAsJsonObject();
        var session = new MinecraftLaunchCommandBuilder.Session(
                "RelianTester", "12345678-1234-1234-1234-123456789abc", "session-token", "12345");
        var spec = new MinecraftLaunchCommandBuilder.LaunchSpec(java, game, version, libraries,
                assets, natives, "1.21.1", "17", "net.minecraft.client.main.Main", 4096, session);

        List<String> command = MinecraftLaunchCommandBuilder.build(metadata, spec);
        assertEquals(java.toString(), command.get(0));
        assertTrue(command.contains("-Xmx4096M"));
        assertTrue(command.contains("session-token"));
        assertTrue(command.contains("RelianTester"));
        assertTrue(command.contains("12345678123412341234123456789abc"));
        assertTrue(command.contains(library.resolve("demo-1.0.jar").toString()));
        assertTrue(command.contains(assets.toString()));
        assertTrue(command.contains(natives.toString()));
    }

    @Test
    void refusesLaunchWithoutMinecraftAccessToken() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftLaunchCommandBuilder.Session(
                "Tester", "12345678-1234-1234-1234-123456789abc", "", ""));
    }
}
