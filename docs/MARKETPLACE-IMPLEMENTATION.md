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

Modrinth modpack import is not implemented yet.

### CurseForge

CurseForge search and direct file installation use the official API and require an API key. Relian reads the key from the `RELIAN_CURSEFORGE_API_KEY` environment variable. Do not commit an API key to this repository or place it in source code.

CurseForge installs currently support individual mods, resource packs, and shaders when the API provides a direct file URL and SHA-1 hash. Downloads are restricted to HTTPS Forge CDN hosts, limited to 300 MB, and verified before installation.

CurseForge modpack import is not implemented yet. Some CurseForge projects may not permit direct downloads through the API; those will display a clear install error and can still be opened on CurseForge's project page.

## Important installation notes

- Select the game directory belonging to the intended Minecraft installation, not the Relian source-code folder.
- Mods must match the selected Minecraft version and loader. Relian does not currently install Fabric/Forge/NeoForge/Quilt loader runtimes.
- Resource packs and shader packs are copied into their standard game directories. A compatible graphics mod (such as Iris or OptiFine, depending on the Minecraft version and loader) may still be needed to use shaders.
- Installation is file-level only at this stage. Relian does not yet resolve required dependencies, check conflicting mods, create isolated profiles, or roll back an installation.
- Review project pages and permissions before installing community content.

## Build and verification status

These features have been committed to the repository, but a successful Windows Gradle build and live API/install test have not yet been confirmed. The next validation step is to run the Windows CI build, then test searches and installs using a disposable Minecraft game directory.
