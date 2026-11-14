# Relian Client Windows launcher

Relian is an early-access Windows-first Minecraft Java Edition launcher built with Java 21 and JavaFX.

## Download and install

1. Open [GitHub Actions](https://github.com/voidcraft455-coder/Relian-Client-V1/actions).
2. Select the newest successful **Build Relian Client for Windows** run.
3. Download the **RelianClient-Windows-Installer** artifact and extract its ZIP.
4. Run the included `.exe` installer. It creates Start-menu and desktop shortcuts.

Only run an installer from this repository's successful workflow. Early-access builds may trigger Windows SmartScreen because they are not code-signed.

## First run

1. Open **Settings** and select **Detect installed Java** or **Download & install compatible Java**. Relian chooses a Java major version based on the selected Minecraft version.
2. Choose the game directory where Relian should store its managed instances and runtimes, or use the default under your user profile.
3. Open **Instances** and create a Vanilla, Relian, Import, or Modpack instance. The launcher downloads Mojang's client metadata, client JAR, libraries, native libraries, and assets, then installs the selected supported loader where applicable.
4. To use Microsoft sign-in, configure a Microsoft public desktop application client ID in Settings. Relian opens the system browser and uses OAuth authorization code with PKCE; it never asks for your Microsoft password inside the launcher.
5. Sign in with a Microsoft account that owns Minecraft Java Edition. The launcher continues through Xbox Live, XSTS, Minecraft Services, Java ownership, and profile checks before creating a launch session.
6. Return to Home, select the active instance's exact Minecraft version and loader, then click **PLAY**.
7. For a Fabric 1.21.1 instance, the bundled Relian HUD is installed automatically. Press **Right Shift** in Minecraft to open HUD Studio.

The Offline / Local Test Profile is only a launcher UI preview. It is not an authenticated Minecraft account and cannot launch the game.

## Implemented features

- Mojang version catalog, official client installation, library/native extraction, asset installation, and checksum validation.
- Java runtime discovery and checksum-verified Eclipse Temurin installation.
- Vanilla and supported Fabric, Quilt, Forge, and NeoForge profile installation, with metadata inheritance handling in the launch path.
- Microsoft browser OAuth with PKCE and Xbox/XSTS/Minecraft Services authentication and Java ownership checks. A valid public application registration is required.
- Isolated instances, instance import, Modrinth modpack import from `.mrpack`, and per-instance manifests.
- Modrinth search and individual mod/resource-pack/shader installation with compatibility filtering and SHA-512 validation.
- CurseForge search and supported individual file installs when RELIAN_CURSEFORGE_API_KEY is configured.
- Installed-content listing, reversible mod enable/disable, and confirmed removal.
- Minecraft-only performance presets, launcher background color, memory settings, and Java runtime settings.
- Local cosmetic asset libraries for skins, capes, wings, and hats. These are validated and previewed locally; they are not yet drawn on player models or synchronized to other players.
- Bundled Fabric 1.21.1 utility HUD runtime with 18 modules and an in-game HUD editor.

## Known limitations and verification

- CI verifies compilation, automated tests, and Windows installer packaging. A CI pass does not prove a successful live Minecraft session on every PC.
- The Microsoft application ID must be supplied/configured by the launcher operator. Real account sign-in has not been verified on every environment.
- The bundled Fabric HUD currently targets Minecraft 1.21.1 only. Other Minecraft versions may launch through supported loaders, but do not receive this HUD.
- Custom capes, wings, and hats are currently local asset-library entries, not equipped in-game cosmetics.
- Marketplace dependency resolution, CurseForge modpack import, one-click modpack updates, and robust conflict resolution remain incomplete.
- Every loader/version/modpack combination still requires real-world testing. Third-party projects can have dependencies, incompatibilities, or provider download restrictions.
- Code signing, automatic self-update, and a public stable release channel are not yet configured.

## Build from source

From `RelianClient-Browser-Login-Update/RelianClient-Windows`, use Java 21 and run:

```powershell
gradle test jpackage
```

The Windows CI workflow builds the Fabric HUD artifact first, bundles it into the launcher resources, runs tests, packages the Windows installer, and uploads the installer artifact.