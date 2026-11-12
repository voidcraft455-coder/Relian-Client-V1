# Relian Client — Updated Product Specification

## Product direction
Relian is a Windows-first Minecraft Java Edition launcher and in-game client. The design should feel polished, compact, and gaming-focused, while using original Relian branding and assets. Do not reproduce Lunar Client's exact logo, icon, wordmark, UI artwork, or proprietary features/code.

## Development order (user-requested)
Build in this order:
1. Modrinth and CurseForge marketplace foundation.
2. Content installation: mods, modpacks, resource packs, and shaders.
3. In-game Relian client framework and HUD.
4. Cosmetics and wardrobe.
5. Finish launcher layout, settings, authentication, and Windows packaging.

Keep the launcher usable during development, but prioritize the marketplace and in-game client before polishing the final launcher.

## Marketplace requirements
- Modrinth integration first, then CurseForge using their supported API and applicable API terms.
- Browse/search/filter mods, modpacks, resource packs, and shaders.
- Filter by Minecraft version, loader, project type, and client/server environment.
- Show author, license, source, supported versions/loaders, dependencies, and file details.
- Support install, uninstall, update, dependency resolution, conflict warnings, and profile backups.
- Fabric is the primary mod loader and must be the default selection wherever supported.
- Forge, NeoForge, Quilt, and Vanilla are secondary options when compatible.

## Main launcher layout
- Left: compact navigation rail.
- Center: one featured installation card for the most-played instance.
- Right: Minecraft character preview and prominent Play button.
- Secondary installations live in a collapsed/small instance selector, not a large list on the home screen.
- Track last-played timestamp and playtime per profile. Rank by playtime, with last-played as a tie-breaker. Provide a manual pin/favorite override.
- Play button belongs on the right side of the featured installation area.
- Character/cosmetic preview belongs on the right side near the Play button, without obstructing it.

## Cosmetics and wardrobe
- Add a Cosmetics page and a Wardrobe page.
- Wardrobe previews the user's Minecraft Java skin and supports selecting/uploading a skin image, subject to Minecraft account capabilities and file validation.
- Cosmetics inventory can include owned/available capes, emotes, hats, accessories, and other cosmetic items only where technically supported and permitted.
- Clearly distinguish account-owned Minecraft cosmetics, Relian-local cosmetics, and server-specific cosmetics.
- Do not imply that a Relian-local cosmetic is visible to other players. Only server/client integrations that explicitly support it may synchronize cosmetics.
- Include equip/unequip, preview, favorites, categories, and saved outfits.
- Use original Relian cosmetic designs; do not copy Lunar's paid cosmetics or artwork.

## In-game client and HUD
- Build a shared module registry with categories, descriptions, enable state, settings, keybinds, profiles, and per-version compatibility.
- Initial modules: FPS, coordinates, direction, keystrokes, CPS display, armor status, potion status, ping, clock, crosshair customization, zoom, screenshot manager, performance diagnostics, and HUD editor.
- Fabric is the primary integration target. Use separate version-specific adapters; never assume one mod binary supports every Minecraft release.
- Keep multiplayer modules compliant with server rules. Do not add automated attacks, player-targeting cheats, anti-cheat evasion, combat bots, or features that expose other players' private information.

## Authentication
- Provide a Microsoft sign-in button that opens the system browser and uses the official OAuth authorization-code flow with PKCE.
- Minecraft Java account authentication must continue through Xbox Live, XSTS, Minecraft Services, ownership checks, and profile lookup after Microsoft identity login.
- An email address is an account identifier, not a separate Minecraft authentication provider. An email/password form must not collect Microsoft passwords or pretend to authenticate Minecraft accounts directly.
- If adding email sign-in for a separate Relian account, implement it through a real identity provider/backend with verification, secure session handling, account recovery, and privacy/security review. Keep this separate from Microsoft/Minecraft ownership authentication.
- Never ship a client secret in the desktop application.

## Background customization
- Settings should offer built-in background presets, solid colors, gradients, and a user-selected local image.
- Include blur/dim overlay and brightness controls so text remains readable.
- Persist background preferences locally per launcher user.
- Validate image formats and size; keep a default background fallback.

## Brand and logo
- Create an original geometric Relian symbol and wordmark. A clean lunar/space-inspired visual direction is acceptable, but the shape and composition must be distinct from Lunar Client branding.
- Provide light/dark variants, app icon, installer icon, and in-launcher lockup.

## Account and privacy
- Keep Microsoft tokens out of logs and source control. Use OS-protected credential storage for persistent refresh tokens.
- Do not store raw passwords.
- Provide sign-out and account removal controls.

## Release and compatibility
- Target Minecraft Java 1.19 through 26.3, plus 1.8.8 and 1.8.9.
- Resolve actual releases and metadata from Mojang's version manifest; do not assume every requested version exists as an official release.
- Select a compatible Java runtime per Minecraft version.
- Label vanilla launch compatibility separately from Relian feature compatibility and third-party mod compatibility.
- Fabric is the default/primary loader, but support depends on loader availability for each Minecraft release.