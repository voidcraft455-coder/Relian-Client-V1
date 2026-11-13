# Relian Client Marketplace

## Current implementation

The Windows launcher now has separate marketplace pages for:

- Mods
- Modpacks
- Resource packs
- Shaders

The project browser supports live search against Modrinth and CurseForge. Search requests run off the JavaFX UI thread, and results show the project name, author, source, description, and a link to the original project page.

### Modrinth

Modrinth search is available without a private API key. The browser filters by project type, Minecraft version, and (for mods) the selected loader.

For mods, resource packs, and shaders, the Install action:

1. Asks the user to choose an existing Minecraft game directory.
2. Requests a file version compatible with the selected Minecraft version and loader where applicable.
3. Downloads only over HTTPS from a Modrinth CDN host.
4. Enforces a 300 MB download ceiling.
5. Verifies the file's SHA-512 digest against Modrinth's metadata.
6. Installs into the standard `mods`, `resourcepacks`, or `shaderpacks` directory using a temporary file and a final move.

#### Modrinth .mrpack import

The Instances page can import a local Modrinth `.mrpack` archive into a new isolated Relian instance.

1. Select a local `.mrpack` file. Relian reads the root `modrinth.index.json` and requires format version 1 and the Minecraft game identifier.
2. The exact Minecraft version and one declared loader (Fabric, Quilt, Forge, or NeoForge) are read from `dependencies`; the loader version is preserved in `relian-modpack.json`. A pack with conflicting loader declarations is rejected.
3. Relian downloads only files whose client environment is `required`. Optional and server-only/unsupported files are skipped.
4. Every download URL must be HTTPS on the standard port, without embedded credentials or fragments. Redirects are checked again, and hosts resolving to local/private addresses are rejected. TLS certificate validation is provided by the Java HTTP client.
5. SHA-1 and SHA-512 are verified when the manifest supplies them. Declared and actual sizes are checked, with 300 MB per downloaded file and 2 GB total import limits.
6. Archive validation rejects absolute paths, traversal, Windows drive/UNC paths, reserved names, duplicate/case-colliding entries, symbolic links, special files, Windows reparse-point entries, encrypted/split/ZIP64 archives, oversized entries, and extreme compression ratios.
7. Only the managed game-content destinations are accepted (mods, config, resource packs, shader packs, selected mod configuration folders, saves, and a small set of video-option files). Account files, launcher profiles, server lists, and unrelated game-root files are not copied.
8. The instance is built in a temporary sibling directory and published under its final name only after the import succeeds. Existing instances are never overwritten; failures clean up the staged instance. Relian's instance, module, and modpack manifests plus performance defaults are retained.

The importer does **not** install Fabric/Forge/NeoForge/Quilt runtimes or make a modpack launchable by itself. Relian performance modules are recorded in the module manifest but are not injected into imported packs automatically, avoiding duplicate/conflicting mods. A compatible loader installer and integrated modpack launch path remain separate milestones.

### CurseForge

CurseForge search and direct file installation use the official API and require an API key. Relian reads the key from the `RELIAN_CURSEFORGE_API_KEY` environment variable. Do not commit an API key to this repository or place it in source code.

CurseForge installs currently support individual mods, resource packs, and shaders when the API provides a direct file URL and SHA-1 hash. Downloads are restricted to HTTPS Forge CDN hosts, limited to 300 MB, and verified before installation.

CurseForge modpack import is not implemented yet. Some CurseForge projects may not permit direct downloads through the API; those will display a clear install error and can still be opened on CurseForge's project page.

## Important installation notes

- Select the game directory belonging to the intended Minecraft installation, not the Relian source-code folder.
- Mods must match the selected Minecraft version and loader. Relian does not currently install Fabric/Forge/NeoForge/Quilt loader runtimes.
- Resource packs and shader packs are copied into their standard game directories. A compatible graphics mod (such as Iris or OptiFine, depending on the Minecraft version and loader) may still be needed to use shaders.
- Individual mod/resource/shader installs remain file-level. Local Modrinth .mrpack imports now create isolated instances and install the archive's required client files and safe overrides, but do not resolve external dependencies beyond the archive manifest or install its loader runtime.
- Modpack import is staged and cleaned up on failure. Existing instances and unrelated user files are not overwritten.
- Review project pages and permissions before installing community content.

## Build and verification status

<https://github.com/voidcraft455-coder/Relian-Client-V1/actions/runs/43519198560> completed successfully on 2026-11-13 for commit `608264fbe95c63eb6af78fc980729dbd095a43b5`. The Windows job ran `gradle --project-prop=relian.minecraft.integration=true test jpackage`; the Gradle test task, jpackage build, and installer artifact upload all succeeded. The Fabric runtime job also succeeded and uploaded its artifact. Offline importer tests cover manifest parsing, required-client filtering, path traversal, symlink rejection, oversized entries, destination safety, override precedence, instance isolation, and separation of Vanilla from Relian modules.

A live Modrinth download/import test still requires a real .mrpack archive and network access. Loader runtime installation and complete modpack launch integration remain unimplemented.
