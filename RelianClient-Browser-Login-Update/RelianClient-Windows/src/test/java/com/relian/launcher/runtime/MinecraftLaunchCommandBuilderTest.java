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
        assertTrue(command.stream().anyMatch(argument -> argument.contains(library.resolve("demo-1.0.jar").toString())));
        assertTrue(command.contains(assets.toString()));
        assertTrue(command.stream().anyMatch(argument -> argument.contains(natives.toString())));
    }


    @Test
    void buildsModdedClasspathUsingInheritedVanillaClientJarAndMavenLibraries() throws Exception {
        Path java = Files.createFile(temp.resolve("java-modded.exe"));
        Path baseVersion = Files.createDirectories(temp.resolve("versions/1.21.1"));
        Path clientJar = Files.createFile(baseVersion.resolve("1.21.1.jar"));
        Path profileVersion = Files.createDirectories(temp.resolve("versions/fabric-loader-0.16.10-1.21.1"));
        Path libraries = Files.createDirectories(temp.resolve("libraries"));
        Path fabricLibrary = Files.createDirectories(libraries.resolve("net/fabricmc/fabric-loader/0.16.10"))
                .resolve("fabric-loader-0.16.10.jar");
        Files.createFile(fabricLibrary);
        Path assets = Files.createDirectories(temp.resolve("assets"));
        Path natives = Files.createDirectories(profileVersion.resolve("natives"));
        Path game = Files.createDirectories(temp.resolve("instance"));
        var metadata = JsonParser.parseString("""
            {
              "id":"fabric-loader-0.16.10-1.21.1",
              "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
              "relianClientJar":"%s",
              "libraries":[{"name":"net.fabricmc:fabric-loader:0.16.10","url":"https://maven.fabricmc.net/"}],
              "arguments":{"jvm":["-Djava.library.path=${natives_directory}","-cp","${classpath}"],
                "game":["--username","${auth_player_name}"]}
            }
            """.formatted(clientJar.toString().replace("\\", "\\\\"))).getAsJsonObject();
        var session = new MinecraftLaunchCommandBuilder.Session(
                "RelianTester", "12345678-1234-1234-1234-123456789abc", "session-token", "12345");
        var spec = new MinecraftLaunchCommandBuilder.LaunchSpec(java, game, profileVersion, libraries,
                assets, natives, "fabric-loader-0.16.10-1.21.1", "", "net.fabricmc.loader.impl.launch.knot.KnotClient", 4096, session);

        List<String> command = MinecraftLaunchCommandBuilder.build(metadata, spec);
        assertTrue(command.stream().anyMatch(argument -> argument.contains(clientJar.toString())));
        assertTrue(command.stream().anyMatch(argument -> argument.contains(fabricLibrary.toString())));
        assertTrue(command.contains("net.fabricmc.loader.impl.launch.knot.KnotClient"));
    }

    @Test
    void mergesLoaderLibrariesAndArgumentsWithInheritedVanillaMetadata() throws Exception {
        Path versions = Files.createDirectories(temp.resolve("versions"));
        Path vanillaDir = Files.createDirectories(versions.resolve("1.21.1"));
        Files.writeString(vanillaDir.resolve("1.21.1.json"), """
            {"id":"1.21.1","mainClass":"net.minecraft.client.main.Main",
             "libraries":[{"name":"org.example:vanilla:1.0"}],
             "arguments":{"jvm":["-Dvanilla=true"],"game":["--username","${auth_player_name}"]}}
            """);
        Path loaderDir = Files.createDirectories(versions.resolve("fabric-loader-0.16.10-1.21.1"));
        var profile = JsonParser.parseString("""
            {"id":"fabric-loader-0.16.10-1.21.1","inheritsFrom":"1.21.1",
             "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
             "libraries":[{"name":"net.fabricmc:fabric-loader:0.16.10"}],
             "arguments":{"jvm":["-Dfabric=true"],"game":["--demo"]}}
            """).getAsJsonObject();

        var resolved = MinecraftLaunchService.resolveInheritedMetadata(temp, profile, 0);
        assertEquals("1.21.1", resolved.baseVersion());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", resolved.metadata().get("mainClass").getAsString());
        assertEquals(2, resolved.metadata().getAsJsonArray("libraries").size());
        assertEquals(2, resolved.metadata().getAsJsonObject("arguments").getAsJsonArray("jvm").size());
        assertEquals(3, resolved.metadata().getAsJsonObject("arguments").getAsJsonArray("game").size());
    }

    @Test
    void refusesLaunchWithoutMinecraftAccessToken() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftLaunchCommandBuilder.Session(
                "Tester", "12345678-1234-1234-1234-123456789abc", "", ""));
    }
}
