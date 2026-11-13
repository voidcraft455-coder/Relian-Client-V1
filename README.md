# Relian Client

Relian is an original Windows-first Minecraft Java launcher and an early Fabric client runtime.

## Download the Windows launcher

1. Open the [latest successful GitHub Actions build](https://github.com/voidcraft455-coder/Relian-Client-V1/actions/runs/43519198560).
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

## Import a Modrinth modpack

1. Download a `.mrpack` file from the modpack's Modrinth project page.
2. Open **Instances**, choose **Modpack**, click **Choose local .mrpack file**, and select the archive.
3. Enter a new instance name and click **Create instance**. Relian reads the Minecraft version and loader version from `modrinth.index.json`; the selectors are not used to override the pack.
4. Relian installs required client files and safe overrides into a temporary isolated instance, checks HTTPS downloads against any provided SHA-1/SHA-512 hashes, then publishes the instance when import succeeds. Optional and server-only files are skipped.

Relian selects the imported instance and then attempts to install the official Minecraft base files. The Fabric/Forge/NeoForge/Quilt loader runtime is not installed yet, so this does **not** mean the modpack is ready to launch. Its exact loader metadata is saved in `relian-modpack.json`.

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

- Windows installer and Fabric runtime CI builds passed for commit `608264fbe95c63eb6af78fc980729dbd095a43b5`; the Windows `test jpackage` task and both artifact uploads succeeded in [workflow run 43519198560](https://github.com/voidcraft455-coder/Relian-Client-V1/actions/runs/43519198560).
- A successful CI build is not the same as a verified live Minecraft session. The launcher/game launch path and the HUDs still need hands-on testing.
- Microsoft sign-in needs the maintainer's public desktop application ID configuration and a real account test.
- Only Vanilla game launching is wired into the launcher. Other loaders are not ready to launch.
- The Fabric HUD runtime currently targets Minecraft Java 1.21.1 with Fabric.
- Local Modrinth .mrpack import now reads the manifest, installs required client files and safe overrides into an isolated instance, checks HTTPS downloads and provided hashes, and rejects unsafe ZIP entries. Optional and server-only files are skipped.
- The importer records the exact Minecraft and loader versions, but it does not install the loader runtime, resolve additional dependencies outside the .mrpack manifest, or provide a complete modpack launch pipeline.
- Imported modpacks include Relian's module manifest and performance defaults; Relian performance mods are not injected automatically to avoid conflicts with the pack.
- Relian does not include combat automation, auto-aim, fast-crystal automation, or anti-cheat bypasses.

## Build

See `RelianClient-Browser-Login-Update/RelianClient-Windows/BUILD-WINDOWS.md` for the Windows launcher build and `relian-client-fabric/README.md` for the Fabric runtime.
