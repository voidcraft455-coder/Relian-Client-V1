# Relian Client marketplace and content installation

## Implemented marketplace pages

The JavaFX launcher has separate pages for Mods, Modpacks, Resource Packs, and Shaders. The provider abstraction supports Modrinth search without a private API key and CurseForge search when the operator supplies `RELIAN_CURSEFORGE_API_KEY`. Search can filter by project type, Minecraft version, and loader where the provider supports those filters.

## Modrinth individual installs

For mods, resource packs, and shaders, the Install action requests a compatible project version, downloads from approved HTTPS Modrinth CDN hosts, enforces a download-size ceiling, verifies the supplied SHA-512 digest, stages the file, and places it into the selected instance's standard `mods`, `resourcepacks`, or `shaderpacks` directory.

## Modrinth modpack import

The Instances page imports a local `.mrpack` archive into a new isolated instance:

1. The importer reads `modrinth.index.json`, validates supported format metadata, and reads Minecraft plus the declared loader from the pack.
2. Required client files are downloaded; optional, server-only, and unsupported environment entries are skipped.
3. HTTPS URLs, redirect destinations, file sizes, archive sizes, and supplied SHA-1/SHA-512 digests are checked.
4. Archive extraction rejects traversal, absolute paths, symlinks, special files, unsafe destinations, duplicate/case-colliding entries, and excessive sizes or compression ratios.
5. Only supported game-content directories and selected safe configuration files are accepted. Account credentials and launcher profile data are not copied.
6. The importer stages the new instance and publishes it only after the import succeeds. It does not overwrite an existing instance.
7. After import, the launcher installs official Minecraft base files and the declared supported loader version where the loader installer supports that target.

The importer preserves third-party pack files; it does not claim to resolve every external dependency or guarantee that every pack launches. The bundled Relian HUD is added only to compatible Fabric 1.21.1 instances.

## CurseForge

CurseForge search, supported individual file installs, and modpack imports use the official API and require the `RELIAN_CURSEFORGE_API_KEY` environment variable. Keys must not be committed to source control. The importer accepts a local CurseForge manifest ZIP, resolves required project/file IDs through the official API, verifies SHA-1 checksums, restricts downloads to trusted HTTPS CDN hosts, enforces per-file and aggregate size limits, extracts only approved override folders, and publishes the new isolated instance only after the import succeeds. Optional manifest files are skipped. Direct downloads remain subject to CurseForge API permissions and provider restrictions.

## Installed content

The Installed Content page scans supported mods, resource packs, and shaders in the active instance. It can refresh the listing, show file size, toggle mods by reversible `.jar` / `.jar.disabled` renaming, and remove managed content after confirmation. It does not inspect or guarantee third-party mod compatibility.

## Remaining marketplace work

- Full dependency graph resolution and installation ordering for individual mods.
- Clearer conflict detection and backup/rollback before replacing installed files.
- Update checking and safe update flows for installed mods, resource packs, and shaders.
- Required dependency graph resolution for Modrinth individual mod installs (CurseForge modpack manifest files are resolved individually, but transitive project dependencies are not recursively discovered).
- Richer metadata display for licenses, supported environments, dependency details, and changelogs.
- End-to-end tests against representative live Modrinth and CurseForge projects, including API-key-enabled CurseForge flows.

## Safety

Use official provider APIs and comply with their terms. Never install server-only mods into client profiles. Keep provider API keys out of source control. Verify hashes when supplied, use staging files, and avoid overwriting user data without explicit confirmation.