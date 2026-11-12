# Relian Client Fabric runtime (initial HUD milestone)

This is the first in-game runtime module, currently targeting **Minecraft Java 1.21.1 + Fabric**. It is intentionally separate from the JavaFX launcher until the launcher has a real loader/game installation and launch pipeline.

## HUD

- Press **Right Shift** to open/close the Relian HUD editor.
- Toggle FPS, coordinates, keystrokes, and armor durability panels.
- Move each panel with the arrow buttons and adjust its scale.
- Settings persist in `config/relian-hud.json`.
- The editor does not pause single-player gameplay.

This is an original Relian layout and visual system. It is not a copy of Lunar Client or Dawn Client assets or source.

## Build

Run `gradle build` from this directory with Java 21 and internet access. The output mod jar is in `build/libs/`.

## Current limits

This is a first runtime milestone, not a full client. It does not implement combat automation, player targeting, hidden-player tracking, or anti-cheat bypasses. The custom Relian resource-loading splash, game launching, additional module types, direct mouse dragging, a fully free-form color picker, and support for other Minecraft versions are future work. The loading overlay currently appears over the vanilla resource-loading splash rather than replacing every early startup frame. The current HUD module has not been benchmarked in-game yet.
