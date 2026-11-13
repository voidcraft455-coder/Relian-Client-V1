# Relian Client

Relian is an original Windows-first Minecraft Java launcher and an early Fabric client runtime.

## Download the Windows launcher

1. Open the [latest successful GitHub Actions build](https://github.com/voidcraft455-coder/Relian-Client-V1/actions/runs/43373102276).
2. Sign in to GitHub if prompted.
3. Scroll to **Artifacts** and download **RelianClient-Windows-Installer**.
4. Extract the downloaded ZIP.
5. Run the included `RelianClient-0.1.0.exe` installer (the exact filename may include a version suffix).
6. Follow the Windows installer prompts. Launch **RelianClient** from the Start menu or desktop shortcut.

Only install artifacts from this repository's successful workflow. Windows may show a SmartScreen warning because this early-access installer is not code-signed; verify that the download came from this repository before deciding whether to run it.

## First launcher test

1. Open **Settings** and select **Download & install compatible Java**. The launcher downloads and verifies an Eclipse Temurin runtime for the selected Minecraft version.
2. Open **Versions** and choose a release. Click **Download client files** and wait for the official client JAR, libraries, Windows natives, and assets to finish downloading and verifying.
3. Return to **Home**. Select the same Minecraft version and choose **Vanilla**.
4. Microsoft sign-in is currently gated on a registered public desktop application ID supplied by the project maintainer in `auth.properties`. The installer does not yet ship with a configured public application ID. If sign-in says the application ID is missing, stop there; do not enter your Microsoft password into any other prompt. The maintainer must configure the public client ID first.
5. Once Microsoft sign-in and Minecraft Java ownership verification are configured and successful, click **PLAY** to test the Vanilla launch path.

The launcher currently supports the Vanilla launch path only. Fabric, Forge, NeoForge, and Quilt game launching are not yet connected. The Fabric HUD runtime is a separate mod artifact and is not automatically injected by the launcher.

## Download the Fabric HUD runtime

The same Actions run publishes **RelianClient-Fabric-1.21.1**. This is a separate Fabric mod artifact for Minecraft Java 1.21.1. Extract its ZIP and use the included JAR with a compatible Fabric 1.21.1 installation. Back up existing game files before testing mods.

Press **Right Shift** in-game to open the Relian HUD editor. HUD settings are saved in `config/relian-hud.json`.

## Current HUD modules

- FPS counter
- Coordinates
- Keystrokes
- Armor status
- Ping
- Held-item durability
- Potion effects
- Direction
- Biome
- Movement speed
- Local clock
- Dimension
- Target block
- Health
- Hunger and saturation
- World time and day
- Server address and player count
- Session timer

Modules can be enabled or disabled, moved, resized, and recolored in the editor.

## Current limitations

- Windows installer and Fabric runtime CI builds passed for the current revision, and both artifacts were uploaded.
- A successful CI build is not the same as a verified live Minecraft session. The launcher/game launch path and the HUDs still need hands-on testing.
- Microsoft sign-in needs the maintainer's public desktop application ID configuration and a real account test.
- Only Vanilla game launching is wired into the launcher. Other loaders are not ready to launch.
- The Fabric HUD runtime currently targets Minecraft Java 1.21.1 with Fabric.
- Marketplace browsing and parts of content management are present, but complete loader installation, full modpack dependency resolution, and integrated Fabric runtime installation are still in progress.
- Relian does not include combat automation, auto-aim, fast-crystal automation, or anti-cheat bypasses.

## Build

See `RelianClient-Browser-Login-Update/RelianClient-Windows/BUILD-WINDOWS.md` for the Windows launcher build and `relian-client-fabric/README.md` for the Fabric runtime.
