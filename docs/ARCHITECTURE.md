# Relian Client architecture and build roadmap

Relian is a Windows-first Minecraft: Java Edition launcher and client project. The launcher is a separate JavaFX application; Minecraft itself runs in an isolated game directory/profile with a version-specific Java runtime and loader.

## Product goals
- Clean, restrained launcher UI inspired by the usability of Lunar Client and Dawn Client. Do not copy their logos, artwork, proprietary code, or exact assets.
- Install and launch official Minecraft releases from 1.19 through 26.3, plus legacy 1.8.8 and 1.8.9.
- Per-version profiles, independent game directories, memory settings, Java runtime selection, and loader selection.
- Browse and install compatible mods and modpacks from Modrinth and CurseForge.
- Browse and install resource packs and shaders with compatibility checks.
- Original Relian HUD and quality-of-life modules, with configurable categories, keybinds, profiles, and settings.
- Windows installer with bundled launcher runtime, Start Menu entry, and desktop shortcut.

## Important compatibility model
A launcher can install vanilla releases across the requested range, but one mod JAR cannot support every Minecraft release. Relian must use version-specific client adapters and build targets.
- The launcher reads Mojang's version manifest and downloads the official client assets/libraries for the selected release.
- The launcher selects a compatible Java runtime for each game version. The launcher runtime is independent of the runtime used by Minecraft.
- Each modded profile records its Minecraft version, loader, loader version, and installed files.
- Modern releases use supported Fabric / Forge / NeoForge targets where available. The legacy 1.8.8 and 1.8.9 targets are separate compatibility projects and must not share modern game hooks.
- A version is marked **Vanilla supported** when it can be installed and launched, and **Relian features supported** only when the matching adapter has passed tests.
- Mod marketplace results must be filtered by game version, loader, project type, and client/server environment before installation.

Minecraft 26.3 currently requires Java 25. Do not hard-code Java 21 as the game runtime for all versions. The launcher may keep its own independently selected Java toolchain while provisioning the correct game runtime.

## Planned project modules
### Launcher
- launcher-ui: JavaFX navigation, home screen, version selector, profiles, settings.
- launcher-core: version manifest, metadata, downloads, checksums, library/native extraction, process launch.
- auth: Microsoft browser sign-in and the complete Xbox/XSTS/Minecraft Services authentication flow. Never collect Microsoft passwords or ship a client secret.
- profiles: isolated instances and per-profile settings.
- installer: Windows packaging and updates.

### Marketplace
- marketplace-api: provider abstraction and normalized project/version models.
- modrinth-provider: search, project details, version filtering, file metadata.
- curseforge-provider: API-backed search and file metadata, subject to API-key and provider terms.
- dependency-resolver: required dependencies, loader compatibility, conflicts, safe file installation.
- content-manager: mods, modpacks, resource packs, and shaders.

### In-game client
- client-common: module registry, settings, keybinds, HUD framework, config persistence.
- client-modern: version-specific modern game integration.
- client-legacy-1.8.8 and client-legacy-1.8.9: isolated legacy integrations.
- features: original performance, HUD, visual customization, and quality-of-life features.

## Client module categories
Initial safe and useful modules:
- HUD: FPS, coordinates, direction, keystrokes, CPS display, armor status, potion status, ping, clock.
- Visual customization: crosshair editor, hit-color feedback, interface scaling, zoom, cosmetic toggles.
- Performance: FPS limiter, memory display, entity/render-distance presets, diagnostics.
- Utility: screenshot manager, waypoint notes for worlds where allowed, profile switcher, config manager.

Multiplayer features must respect server rules. Do not implement features that automate attacks, target other players, evade server protections, or expose other players' private information. Any experimental modules should be disabled by default and clearly labeled.

## Marketplace safety and reliability
- Use official provider APIs and comply with each provider's API terms and attribution requirements.
- Show project author, license, supported Minecraft versions, supported loaders, environment, dependencies, and source page.
- Never silently install a server-only mod into a client profile.
- Verify downloaded file hashes when provided; write files to a staging area before activation.
- Back up profile manifests before changing installed mods.
- Do not re-host third-party mod files without permission.

## Development milestones
1. Foundation: launcher shell, navigation, settings, profile model, version manifest reader.
2. Vanilla launching: download metadata/assets/libraries, Java runtime provisioning, process launch, logs.
3. Authentication: complete Microsoft-to-Minecraft authentication and secure token storage.
4. Marketplace: Modrinth first, then CurseForge; compatibility filtering and dependency resolver.
5. Content management: modpacks, resource packs, shaders, backups, updates.
6. Modern client: shared module framework and the first modern version adapter.
7. Legacy client: isolated 1.8.8 and 1.8.9 adapters.
8. Windows release: signed/reproducible build, installer, update channel, crash reporting.

## Current repository status
The existing repository contains an early JavaFX launcher prototype, a Microsoft browser OAuth foundation, and a Windows packaging workflow. It is not yet a complete Minecraft launcher: the full Xbox/XSTS/Minecraft Services authentication, official version installation/launching, marketplace, mod loader installation, and in-game client modules still need implementation.

## First implementation target
Build the version catalog and isolated profile model before implementing the marketplace. This gives every later feature a reliable Minecraft version + loader + Java runtime target.