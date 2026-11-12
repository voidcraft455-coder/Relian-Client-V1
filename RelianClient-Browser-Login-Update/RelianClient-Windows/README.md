# Relian Client (early development)

Relian Client is a Windows-first Minecraft Java launcher prototype built with Java 21 and JavaFX.

## Implemented foundations

- Windows installer packaging through GitHub Actions and Gradle `jpackage`.
- Mojang official release catalog for releases from 1.19 onward, plus 1.8.8 and 1.8.9.
- Modrinth marketplace search for mods, modpacks, resource packs, and shaders.
- CurseForge marketplace search through its official API when `RELIAN_CURSEFORGE_API_KEY` is configured.
- Individual Modrinth and CurseForge mod/resource-pack/shader downloads with file-type checks, size limits, trusted HTTPS download hosts, and publisher checksum verification when metadata is available.
- Four-choice instance creator: clean Vanilla, import an existing instance, Relian client profile, and Modrinth modpack browsing/import.
- Isolated instance directories with a Relian manifest; non-Vanilla choices include the Relian module manifest.
- Safe import foundation for Prism/MultiMC-style folders and game content from other clients: mods, configs, resource packs, shaders, saves, screenshots, and options. Launcher/account credentials are not copied.
- Minecraft-only FPS video presets for new and existing instances. They tune render/simulation distance, particles, clouds, biome blending, entity shadows, VSync, and graphics quality without changing Windows process priority or restricting other apps.
- Fabric profiles attempt to install compatible Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, and Dynamic FPS releases from Modrinth with checksum verification.
- Saved launcher background color, Minecraft memory-limit preference, and selected game-directory preference.
- Installed Content page that scans the selected game directory's `mods`, `resourcepacks`, and `shaderpacks` folders and supports confirmed removal of individual `.jar` / `.zip` files.
- Microsoft browser OAuth foundation using authorization-code flow with PKCE.

## Important unfinished features

This is not yet a complete Minecraft launcher. The Play button is still a placeholder. Official Minecraft installation and game launching, Fabric/Forge/NeoForge/Quilt loader installation, automatic loader setup from a modpack manifest, additional project dependency resolution beyond the files explicitly listed in the archive, loader-specific Relian module sets for Forge/NeoForge/Quilt, Xbox/XSTS/Minecraft Services authentication, entitlement checks, and secure long-lived credential storage remain to be implemented and tested.

Microsoft OAuth currently authenticates the Microsoft identity only. It does not prove Minecraft ownership and does not make the game launchable. Never add a Microsoft client secret or personal account password to this project.

The memory-limit setting is a saved preference for a future game-launch process; it does not change the launcher's own JVM memory allocation yet. The FPS presets only edit the selected Minecraft instance's options.txt. They are quality/performance trade-offs, not a guaranteed FPS increase, and they do not change Windows CPU/GPU priorities or other applications' resource access. The preset's 260 FPS ceiling applies only inside that Minecraft instance.

The Installed Content page only manages individual files in the three standard content folders. It does not resolve dependencies, remove config files, manage modpack profiles, or guarantee that removing a mod will leave another mod working.

See `BUILD-WINDOWS.md` before building and `docs/MARKETPLACE-IMPLEMENTATION.md` for marketplace behavior and current limitations.

## Instance creation and FPS settings

The Instances page provides four paths:
1. **Vanilla** creates a clean profile without Relian modules.
2. **Import** copies supported game content from a selected instance folder. Prism/MultiMC-style `.minecraft` subfolders are detected; account tokens and launcher credentials are excluded.
3. **Relian** creates a Fabric-first profile and attempts to install compatible Modrinth performance mods.
4. **Modpack** searches Modrinth, then imports a matching `.mrpack` archive into the new isolated profile. The importer reads `modrinth.index.json`, checks Minecraft and loader metadata, downloads client-required files from the verified HTTPS `cdn.modrinth.com` host, verifies SHA-512/SHA-1 hashes when supplied, and imports the archive's `overrides/` files. Server-only `server-overrides/` content and files marked client-unsupported are skipped. The importer rejects path traversal, symbolic links, duplicate destinations, Windows-invalid/reserved paths, credential/launcher paths, oversized entries, and attempts to overwrite existing files. A failed import removes the incomplete newly-created Modpack instance and its staged/partially installed files.

Every non-Vanilla profile receives a Relian module manifest. The automatic Relian performance-module download currently targets Fabric only; selecting Forge, NeoForge, or Quilt records the modules but does not yet install a compatible loader-specific set. A loader installer and the game launch pipeline are still required before imported mods can run. The `.mrpack` importer has bounded archive handling (1 GB archive/expanded limits, 20,000 entries, 300 MB per downloaded file, and 32 MB per override / 128 MB total overrides); archives requiring downloads from non-Modrinth-CDN hosts are rejected. The Windows GitHub Actions workflow runs Gradle tests and creates the installer for pull requests targeting `main`. The importer installs only the exact client files listed by the pack index and does not independently resolve extra project dependencies.

FPS settings are Minecraft-local. They lower a few expensive visual settings in the selected profile and do not reserve or throttle Windows CPU, GPU, or memory for other programs. Actual FPS depends on hardware, shaders, worlds, drivers, and mod compatibility. No FPS benchmark has been run as part of this repository change.
