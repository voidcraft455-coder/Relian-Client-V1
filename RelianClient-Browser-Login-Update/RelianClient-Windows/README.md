# Relian Client Windows launcher

Relian is an early-access Windows-first Minecraft Java launcher built with Java 21 and JavaFX.

## Download and install

Use the `RelianClient-Windows-Installer` artifact from the latest successful [GitHub Actions build](https://github.com/voidcraft455-coder/Relian-Client-V1/actions/runs/43373102276). Download the artifact ZIP, extract it, run the included `.exe` installer, and follow the prompts. The installer creates Start-menu and desktop shortcuts.

## First-run guide

1. Open **Settings**.
2. Under **Minecraft Java runtime**, click **Download & install compatible Java**. Relian downloads and checksum-verifies Eclipse Temurin for the currently selected Minecraft version.
3. Open **Versions** and click **Download client files** beside the version you want. Wait for the official client JAR, libraries, Windows native archives, and asset objects to finish installing and verifying.
4. Return to **Home**, select that same version, and choose **Vanilla** in the loader menu.
5. Microsoft browser sign-in is wired to the launcher, but it requires the project maintainer to register a public desktop application and configure its application ID in `auth.properties`. The current installer does not include a configured application ID. Until the maintainer supplies that configuration, Minecraft sign-in and the Play action cannot complete. Never provide your Microsoft password to an unofficial prompt.
6. After the application ID is configured, sign in with the Microsoft account that owns Minecraft Java Edition. Relian checks Xbox access and Minecraft entitlement before enabling a launch session.
7. Click **PLAY** to test the Vanilla launch path. If the game fails to start, record the exact status message and attach the launcher console output when reporting the issue.

## Implemented foundations

- Windows installer packaging through Gradle `jpackage`.
- Mojang official release catalog, client JAR installation, library/native installation, and asset downloads with checksum verification.
- Java runtime detection and checksum-verified Eclipse Temurin installation.
- Vanilla launch command construction and Minecraft process management.
- Microsoft browser OAuth with PKCE and Xbox/XSTS/Minecraft Services authentication flow; a registered public application ID is still required.
- Instance management and isolated profile foundations.
- Modrinth marketplace search and download foundations, plus CurseForge search when an API key is configured.
- Resource-pack, shader, mod, and modpack browsing foundations.
- Installed-content listing and safe file-removal foundations.
- Minecraft-local performance presets and launcher preferences.

## Current limitations

- The Windows build and installer artifact are verified by CI, but a real end-to-end sign-in and Minecraft launch has not yet been verified on a user's PC.
- Microsoft public application ID configuration is required before sign-in works.
- Only Vanilla launching is wired. Fabric, Forge, NeoForge, and Quilt launching are not ready.
- The separate Fabric 1.21.1 HUD runtime is not automatically installed by this launcher.
- Marketplace and modpack features are not a complete dependency-resolving mod manager. Full loader integration and actual `.mrpack` installation remain in progress.
- Do not treat a successful installer build as proof that every feature is complete.
