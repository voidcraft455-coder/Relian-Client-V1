# Relian account and cosmetics implementation

## Microsoft account flow

Relian uses the Microsoft public-client authorization-code flow with PKCE in the system browser. The launcher then exchanges the Microsoft token through Xbox Live and XSTS, requests a Minecraft Services session, verifies Java entitlement, and loads the Java profile. The launcher never asks the user to type a Microsoft password into Relian.

### App registration

The launcher requires a Microsoft Entra public desktop application ID supplied by the project operator/user. The app registration must enable public client flows, use the loopback localhost redirect expected by the launcher, and request the `XboxLive.signin` and `offline_access` scopes. No client secret belongs in the desktop application.

### Session persistence

On Windows, Relian stores only the Microsoft OAuth refresh token in the current user's Local App Data folder. The token bytes are protected with Windows DPAPI for the current Windows user before they are written. Access tokens, Xbox tokens, XSTS tokens, and Minecraft access tokens are not persisted by this feature. On startup, Relian attempts a refresh and repeats the Xbox/XSTS/Minecraft entitlement and profile checks. A failed silent refresh leaves the user signed out and allows an interactive sign-in.

The account button disconnects the active Minecraft session and removes the DPAPI-protected refresh-token file. If Windows DPAPI is unavailable, sign-in can still work for the current launcher session, but persistent sign-in is not available.

### Relian email account flow

The launcher now includes a Supabase Auth REST client and Settings controls for registration, password sign-in, one-time-code password recovery, and sign-out. It uses the project's HTTPS Auth API and public anon/publishable key only; a service-role key must never be placed in the desktop app. Passwords are sent only to the configured identity provider over HTTPS and are not written to disk or launcher preferences. If email confirmation is enabled, registration may return without a session; the user completes the provider's confirmation link or enters the one-time `{{ .Token }}` code in Relian Settings to verify the address and establish a session. For password recovery, Relian verifies the email OTP and submits the new password using the short-lived recovery access token.

Configure these values in Settings:
- Supabase project URL, for example `https://your-project.supabase.co`
- Supabase public anon/publishable key

The same values may be supplied using `RELIAN_SUPABASE_URL` and `RELIAN_SUPABASE_ANON_KEY`. Enable email/password authentication, email confirmation, and password recovery in the Supabase dashboard. Configure the password-recovery email template to show the one-time `{{ .Token }}` code; the user enters that code in Relian along with a new password. Configure the confirmation email template and Site URL according to the Supabase project settings. If using OTP confirmation, include `{{ .Token }}` in the template. Relian does not host the identity database or email service itself.

On Windows, the Relian identity refresh token and email are protected together with current-user DPAPI in `%LOCALAPPDATA%\\Relian\\secure\\relian-account.dpapi`. The short-lived access token stays in memory. A saved session is refreshed on startup. The Relian account is separate from Microsoft sign-in and does not grant Minecraft ownership or game-launch access.

The project operator still needs to create and configure the Supabase project, publish a privacy policy, and configure the email templates and redirect domain before real public registration can work. The desktop integration is implemented, but it cannot be live-verified without those project-specific settings. The offline/local test profile remains UI-preview-only and cannot launch Minecraft.

## Wardrobe and in-game attachments

The launcher validates local PNG assets and stores the selected cape, wings, and hat in the wardrobe. Export copies the equipped assets into the selected instance at `config/relian/cosmetics/` and writes `wardrobe.json`.

The Fabric 1.21.1 client reads that manifest with path, file-size, and PNG-signature checks, then registers the imported PNGs as dynamic Minecraft textures. A player-renderer feature applies those textures to local 3D cape, wing, and hat attachments when the corresponding slots are equipped. Texture resources are refreshed when an equipped file changes. These attachments are client-side decorative geometry and are not synchronized to other players or server-side accounts. The attachment geometry is intentionally a simple flat/low-poly representation; it does not reproduce every pixel layout or animation convention used by third-party cosmetic formats.

Press Right Shift to open HUD Studio. The Cosmetics module can be moved, resized, recolored, or disabled like other HUD modules.


## Minecraft versions, loaders, and installation

The launcher obtains supported releases from Mojang's version manifest and installs the base version metadata, client JAR, libraries, Windows native archives, logging configuration, and asset objects with checksum verification. A modded instance installs the vanilla parent first, then the selected loader profile and loader dependencies. The launch service resolves inherited metadata so loader libraries and arguments are combined with the parent Minecraft metadata and the correct vanilla client JAR.

Supported loader installers:
- Fabric and Quilt: official loader metadata/profile endpoints, compatible-version selection, dependency installation, and isolated profile metadata.
- Forge and NeoForge: official installer artifacts with published SHA-1 verification, execution with the compatible Java runtime, and post-install profile discovery/validation.

Java selection is based on Minecraft's required major version. The launcher detects local installations or downloads a checksum-verified Eclipse Temurin Windows runtime. Java runtime installation and Minecraft/loader installation are separate steps. Some older/newer Minecraft and loader combinations are not published by their upstream maintainers; Relian reports unavailable combinations instead of fabricating a profile.

The Windows CI integration test verifies a Mojang release installation and checks the client JAR, libraries, Windows natives, and assets. A successful CI install does not replace a manual launch test with a real Microsoft-owned Minecraft Java account.
