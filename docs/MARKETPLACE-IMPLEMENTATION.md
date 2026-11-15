# Relian Client marketplace and content installation

## Marketplace browsing

The JavaFX launcher has separate pages for Mods, Modpacks, Resource Packs, and Shaders. Modrinth search works without a private API key. CurseForge search and direct file installation use the official API and require `RELIAN_CURSEFORGE_API_KEY` to be supplied by the launcher operator. Search filters by project type, Minecraft version, and loader where the provider supports those filters. Result cards show provider, author, type, description, and available categories.

## Modrinth installs

For mods, resource packs, and shaders, installation selects a compatible project version, downloads only from approved HTTPS Modrinth CDN hosts, enforces a 300 MB download ceiling, verifies the publisher-provided SHA-512 digest, and stages the file before publishing it. Mod installs recursively resolve required dependencies, install dependencies before the selected mod, respect pinned version IDs, validate Minecraft and loader compatibility, skip already-installed dependencies, and guard against cycles. Optional and incompatible dependencies are not installed automatically. If a required dependency or parent install fails, files published by that install operation are rolled back.

## CurseForge installs

CurseForge individual mod, resource-pack, and shader installs use the official API, verify SHA-1 hashes, enforce the download ceiling, and restrict downloads to trusted HTTPS Forge CDN hosts. Required mod dependencies (relation type 3) are resolved recursively; pinned file IDs are respected, dependencies install before the selected mod, and already-installed dependencies are not duplicated. Optional and incompatible dependencies are not installed automatically. A failure rolls back all files published by the operation. CurseForge API key access and direct-download permissions remain controlled by CurseForge.

## Modpack import

The marketplace Modpacks page can download a compatible Modrinth `.mrpack` or CurseForge modpack ZIP, verify its provider checksum, and import it directly into a new isolated Relian instance. The Instances page also supports importing local Modrinth `.mrpack` and CurseForge manifest ZIP archives. Importers validate archive paths and file metadata, verify supplied hashes, enforce file/aggregate size limits, and stage the instance before publishing it. CurseForge manifest project/file IDs are resolved through the official API. Unsupported optional or server-only files are skipped. Importers do not promise that every third-party pack launches; runtime compatibility is verified separately in Phase 5.

## Installed content and backups

The Installed Content page scans mods, resource packs, and shaders in the selected game directory. It supports reversible enable/disable, removal, content backups, and restore without overwriting existing files. Backups are stored separately from live content and restore is staged and rolled back if publishing fails.

## Install ownership and updates

Successful Modrinth and CurseForge installs are recorded in `.relian/marketplace-installs.json` with provider project ID, selected version/file ID, verified checksum, content type, game version, loader, and relative installed path. Records survive enable/disable renames. The Installed Content page can check for compatible provider updates and, after confirmation, install replacements before retiring old files. The previous file is saved as a ZIP in `.relian-backups`, where it is also visible to the existing Restore backup control; a failed replacement is removed and the previous file is restored. Manually added files are not included in provider update checks because they have no trusted project/version metadata.

## Conflict and compatibility handling

Marketplace mod installs inspect common Fabric, Forge, and NeoForge metadata and reject duplicate mod identifiers before publishing a JAR. Provider version queries filter by Minecraft version and selected loader; pinned dependencies are checked again before download. These checks reduce common conflicts but cannot prove that arbitrary third-party mods are mutually compatible. The launcher must still report Minecraft crash logs and actual loader/runtime tests are part of Phase 5.

## Safety and remaining external constraints

- Provider API keys must never be committed to source control or embedded in the launcher.
- Respect provider download permissions and rate limits.
- Files with missing or invalid checksums are rejected.
- Existing content is not overwritten during normal installation.
- Update application is user-confirmed and keeps a copy of the previous file.
- Live integration tests still require valid provider access and representative projects; the CI build verifies compilation and automated tests, not external API availability or in-game behavior.
