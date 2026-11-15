# Relian Client

Relian is an original Windows-first Minecraft Java launcher with a separate Fabric 1.21.1 client runtime.

## Download the Windows launcher

1. Open [GitHub Actions](https://github.com/voidcraft455-coder/Relian-Client-V1/actions).
2. Select the newest **successful** run of **Build Relian Client for Windows**.
3. Under Artifacts, download **RelianClient-Windows-Installer** and extract the ZIP.
4. Run the included Windows installer and launch Relian Client from the Start menu or desktop shortcut.

Only install artifacts from this repository's successful workflow. The early-access installer may trigger SmartScreen because it is not code-signed. Verify the repository and workflow source before choosing to run it.

## First launch

1. Open **Settings** and choose **Download & install compatible Java** (or detect an existing Java runtime).
2. Choose a Minecraft game directory.
3. Open **Versions**, select a Minecraft release, and install its official client files.
4. Configure a public Microsoft desktop application client ID in Settings if it has not been configured. The launcher uses browser sign-in and ownership verification; it does not ask for a Microsoft password inside the launcher.
5. Sign in with a Microsoft account that owns Minecraft Java Edition. The offline/local test profile is only a launcher UI preview and cannot launch the game.
6. Create or select an instance, then use Home to launch the matching Minecraft version and loader.

## Phase 3 features

- **Instances:** create isolated Vanilla, imported, Relian, and Modrinth modpack instances; select, open, and remove Relian-managed instances.
- **Loaders:** install supported Fabric, Quilt, Forge, and NeoForge profiles for the selected Minecraft version.
- **Modpacks:** search Modrinth projects, open their project pages, and import downloaded `.mrpack` archives into new isolated instances. CurseForge modpack ZIP imports are also supported when `RELIAN_CURSEFORGE_API_KEY` is configured; required manifest file IDs are resolved through the official API and publisher SHA-1 hashes are checked. The importer checks manifest paths, rejects unsafe ZIP entries and symlinks, limits download sizes, validates HTTPS destinations, and verifies supplied SHA-1/SHA-512 hashes.
- **Marketplace:** browse supported Modrinth and CurseForge project listings. Modrinth single-file installs check Minecraft/loader compatibility and verify SHA-512; supported CurseForge single-file installs use the official API and provider checksums.
- **Installed content:** scan mods, resource packs, and shader packs; enable or disable supported files with reversible filename changes; remove files from managed folders with confirmation.
- **Performance profiles:** apply Minecraft-only video settings to a selected instance.
- **HUD Studio:** the launcher bundles its Fabric 1.21.1 HUD runtime into the Windows installer. Compatible Fabric 1.21.1 instances receive the HUD automatically after loader installation; existing instances have an **Install Relian HUD** action. The 27 utility modules support visibility, color, scale, dragging, and arrow-key positioning. Settings persist in `config/relian-hud.json`.
- **Cosmetic wardrobe:** organize local PNG assets in separate Skin, Cape, Wings, and Hat tabs. Imports are size-validated, limited to 2 MB, stored in category-specific folders, previewed in the launcher, and safely removable. Skin imports also retain the existing skin-library workflow. The current in-game runtime does not yet render custom capes, wings, or hats; the wardrobe is an asset library, not an equip/visibility claim. Use the official Minecraft skin-management page to equip an account skin.

## Fabric HUD runtime

The Fabric mod is built as the **RelianClient-Fabric-1.21.1** Actions artifact and bundled into the Windows installer. When a Fabric 1.21.1 instance is created or its loader is installed, Relian copies the verified mod JAR into that instance's `mods` folder. Existing compatible instances can use **Install Relian HUD** in the Instances page. Press **Right Shift** in-game to open HUD Studio.

Modules include FPS, coordinates, keystrokes, armor status, ping, held-item durability, potion effects, direction, biome, movement speed, local clock, dimension, target block, health, hunger/saturation, world time/day, server/player count, session time, block light level, chunk coordinates, XYZ velocity, offhand item, local date, armor condition, experience, weather, and view angles.

## Verification and limitations

- CI builds and automated tests verify compilation and selected file-management/import behavior. A successful CI run does **not** prove that the launcher has been tested on your computer or that Minecraft has completed a live in-game session.
- Microsoft sign-in still needs a correctly configured public application ID and a real account sign-in/ownership test.
- The launcher installs loader profiles, but every Minecraft version and every third-party modpack combination still needs runtime testing.
- The current wardrobe is a local asset library: imported capes, wings, and hats are not yet rendered on player models or synchronized to servers. Do not treat them as equipped or multiplayer-visible cosmetics.
- The current marketplace still lacks recursive dependency-graph installation for individual mods, update discovery for already-installed files, and automated conflict detection/rollback. CurseForge modpack imports are supported but require an operator-provided API key and may be constrained by provider download permissions.
- The bundled Fabric HUD runtime and its automatic instance installer pass CI compilation/tests, but still need a live Minecraft 1.21.1 test.
- Modpack import installs required client files and safe overrides into an isolated instance, then the launcher installs the matching Minecraft base and loader profile when supported. Some packs may still need extra dependencies or manual troubleshooting. The Relian HUD is added only to Fabric 1.21.1 packs to avoid injecting incompatible files.
- Relian focuses on utility HUDs and legitimate client features. It does not provide combat automation, auto-aim, fast-crystal automation, hidden-player tracking, or anti-cheat bypasses.

## Build

See `RelianClient-Browser-Login-Update/RelianClient-Windows/BUILD-WINDOWS.md` for the Windows launcher and `relian-client-fabric/README.md` for the Fabric runtime.
