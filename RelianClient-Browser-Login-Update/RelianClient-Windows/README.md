# Relian Client (early development)

A dark JavaFX launcher prototype with a Windows installer build and a Microsoft browser sign-in foundation using OAuth 2.0 authorization-code flow with PKCE.

The current sign-in implementation requires Relian's own Microsoft Entra public desktop application ID. It completes Microsoft identity sign-in only; Xbox/XSTS/Minecraft Services authentication and game launching are not implemented yet. Mod, resource-pack, and shader pages are UI placeholders.

See `BUILD-WINDOWS.md` before building. Never add a Microsoft client secret or personal account password to this project.
