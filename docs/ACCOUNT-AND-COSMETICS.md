# Relian account and cosmetics implementation

## Microsoft account flow

Relian uses the Microsoft public-client authorization-code flow with PKCE in the system browser. The launcher then exchanges the Microsoft token through Xbox Live and XSTS, requests a Minecraft Services session, verifies Java entitlement, and loads the Java profile. The launcher never asks the user to type a Microsoft password into Relian.

### App registration

The launcher requires a Microsoft Entra public desktop application ID supplied by the project operator/user. The app registration must enable public client flows, use the loopback localhost redirect expected by the launcher, and request the `XboxLive.signin` and `offline_access` scopes. No client secret belongs in the desktop application.

### Session persistence

On Windows, Relian stores only the Microsoft OAuth refresh token in the current user's Local App Data folder. The token bytes are protected with Windows DPAPI for the current Windows user before they are written. Access tokens, Xbox tokens, XSTS tokens, and Minecraft access tokens are not persisted by this feature. On startup, Relian attempts a refresh and repeats the Xbox/XSTS/Minecraft entitlement and profile checks. A failed silent refresh leaves the user signed out and allows an interactive sign-in.

The account button disconnects the active Minecraft session and removes the DPAPI-protected refresh-token file. If Windows DPAPI is unavailable, sign-in can still work for the current launcher session, but persistent sign-in is not available.

### Identity-provider boundary

A separate Relian email/password account is not implemented. It requires a real hosted identity service, verified email flow, account recovery process, privacy policy, and server-side credentials. The desktop launcher must not simulate this by storing passwords locally or treating an unverified local profile as an authenticated Relian identity. The offline/local test profile remains UI-preview-only and cannot launch Minecraft.

## Wardrobe and in-game attachments

The launcher validates local PNG assets and stores the selected cape, wings, and hat in the wardrobe. Export copies the equipped assets into the selected instance at `config/relian/cosmetics/` and writes `wardrobe.json`.

The Fabric 1.21.1 client reads that manifest with path, file-size, and PNG-signature checks, then registers the imported PNGs as dynamic Minecraft textures. A player-renderer feature applies those textures to local 3D cape, wing, and hat attachments when the corresponding slots are equipped. Texture resources are refreshed when an equipped file changes. These attachments are client-side decorative geometry and are not synchronized to other players or server-side accounts. The attachment geometry is intentionally a simple flat/low-poly representation; it does not reproduce every pixel layout or animation convention used by third-party cosmetic formats.

Press Right Shift to open HUD Studio. The Cosmetics module can be moved, resized, recolored, or disabled like other HUD modules.
