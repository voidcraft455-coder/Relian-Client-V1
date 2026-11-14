# Relian Client architecture and project status

Relian is a Windows-first Minecraft: Java Edition launcher with a separate version-specific in-game client runtime. The launcher is a JavaFX desktop application; Minecraft runs in an isolated instance directory with its own version metadata, libraries, assets, native libraries, loader profile, and game options.

## Repository layout

- `RelianClient-Browser-Login-Update/RelianClient-Windows`: JavaFX launcher, authentication, version catalog/install, runtime provisioning, loader installation, marketplace, instances, cosmetics asset library, and Windows packaging.
- `relian-client-fabric`: Fabric 1.21.1 client HUD runtime.
- `.github/workflows/build-windows.yml`: builds the Fabric runtime, bundles it into the Windows launcher, runs Java tests, packages the Windows installer, and uploads artifacts.
- `docs`: architecture, product requirements, marketplace behavior, and release status.

## Current implemented capabilities

### Launcher and game runtime

- Mojang version manifest and release selection.
- Official Minecraft version metadata and client JAR downloads with hash validation.
- Minecraft libraries, Windows native libraries, asset index, and asset downloads.
- Java runtime discovery and checksum-verified Eclipse Temurin provisioning.
- Launch command construction using inherited version metadata, JVM/game arguments, classpath, asset paths, and native paths.
- Process start/stop monitoring and console output routing.
- Instance creation, selection, import foundations, per-instance manifests, safe deletion, and directory separation.
- Fabric, Quilt, Forge, and NeoForge installation support through their respective installer metadata where available.

### Authentication

- Microsoft system-browser OAuth authorization-code flow with PKCE.
- Xbox Live and XSTS exchange, Minecraft Services login, Java entitlement check, and profile retrieval.
- A valid public Microsoft desktop application ID must be configured. The launcher does not collect Microsoft passwords.
- The local test profile is UI-only and cannot launch Minecraft.

### Marketplace and content

- Modrinth search and supported individual file installs with compatibility filtering and checksum validation.
- CurseForge search and supported individual file installs when an API key is configured.
- Modrinth `.mrpack` import into a staged isolated instance, with path, URL, hash, and size validation.
- Installed-content listing, reversible mod toggles, and confirmed removal.
- Resource pack and shader browsing/install foundations.

### In-game client

- Fabric 1.21.1 runtime with 18 configurable utility HUD modules.
- Right Shift opens HUD Studio; settings persist locally.
- The HUD runtime is bundled into the Windows installer and automatically installed into compatible Fabric 1.21.1 instances.

### Cosmetics

- Local skin, cape, wing, and hat PNG libraries with validation, preview, and safe deletion.
- These assets are not yet rendered on the player model or synchronized to other players.

## Verification boundaries

A successful GitHub Actions run verifies the specific code revision's build, automated tests, and Windows packaging steps. It does not prove that Microsoft sign-in succeeds with the operator's app registration, that every Minecraft version or loader starts on physical Windows hardware, that all marketplace projects and modpacks are compatible, or that HUD modules render correctly in a live Minecraft session. Those require live integration tests and sometimes external configuration or user-provided accounts.

## Remaining project milestones

1. **Stabilization:** keep Windows CI green, add tests for launcher UI state and game launch metadata, and fix any live errors reported by users.
2. **Marketplace completeness:** dependency graph resolver, backup/rollback, update checks, conflict warnings, richer metadata, and CurseForge modpack support where permitted.
3. **Cosmetic rendering:** implement and test version-specific player-model rendering for local capes, wings, and hats; clearly label local-only visibility.
4. **HUD expansion:** improve editor controls and add safe utility modules with per-version adapters and settings migration.
5. **Release engineering:** code signing, versioned release artifacts, update channel, crash reports with privacy controls, and reproducible build documentation.
6. **Compatibility validation:** live test Vanilla and supported loader profiles across a representative set of versions and real modpacks.

Multiplayer features must respect server rules. Relian will focus on utility HUDs, performance tools, visual customization, and legitimate client features rather than automated attacks, targeting cheats, anti-cheat evasion, or hidden player tracking.