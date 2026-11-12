# Relian Client (early development)

Relian Client is a Windows-first Minecraft Java launcher prototype built with Java 21 and JavaFX.

## Implemented foundations

- Windows installer packaging through GitHub Actions and Gradle `jpackage`.
- Mojang official release catalog for releases from 1.19 onward, plus 1.8.8 and 1.8.9.
- Modrinth marketplace search for mods, modpacks, resource packs, and shaders.
- CurseForge marketplace search through its official API when `RELIAN_CURSEFORGE_API_KEY` is configured.
- Individual Modrinth and CurseForge mod/resource-pack/shader downloads with file-type checks, size limits, trusted HTTPS download hosts, and publisher checksum verification when metadata is available.
- Saved launcher background color, Minecraft memory-limit preference, and selected game-directory preference.
- Installed Content page that scans the selected game directory's `mods`, `resourcepacks`, and `shaderpacks` folders and supports confirmed removal of individual `.jar` / `.zip` files.
- Microsoft browser OAuth foundation using authorization-code flow with PKCE.

## Important unfinished features

This is not yet a complete Minecraft launcher. The Play button is still a placeholder. Minecraft installation/profile creation, loader installation, modpack importing, dependency resolution, installed-content management, game process launching, Xbox/XSTS/Minecraft Services authentication, entitlement checks, and secure long-lived credential storage remain to be implemented and tested.

Microsoft OAuth currently authenticates the Microsoft identity only. It does not prove Minecraft ownership and does not make the game launchable. Never add a Microsoft client secret or personal account password to this project.

The memory-limit setting is a saved preference for a future game-launch process; it does not change the launcher's own JVM memory allocation yet. Use a conservative value and leave enough RAM for Windows and other applications.

The Installed Content page only manages individual files in the three standard content folders. It does not resolve dependencies, remove config files, manage modpack profiles, or guarantee that removing a mod will leave another mod working.

See `BUILD-WINDOWS.md` before building and `docs/MARKETPLACE-IMPLEMENTATION.md` for marketplace behavior and current limitations.
