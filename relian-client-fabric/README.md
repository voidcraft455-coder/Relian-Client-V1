# Relian Client Fabric runtime (HUD milestone)

This is the first in-game runtime module, currently targeting **Minecraft Java 1.21.1 + Fabric**. It remains separate from the JavaFX launcher until the launcher has a complete loader/game installation and launch pipeline.

## HUD

- Press **Right Shift** to open/close the Relian HUD editor.
- HUD modules: FPS, coordinates, keystrokes, armor durability, ping, and held-item durability.
- The editor uses rounded panels and custom rounded controls.
- Toggle modules, select a module, change its preset color, adjust scale, and drag in the preview area to change its position.
- Settings persist in `config/relian-hud.json`.
- The HUD draws rounded translucent panels in-game.
- The editor does not pause single-player gameplay.

This is an original Relian layout and visual system. It does not use Lunar Client or Dawn Client assets or source.

## Build

Run `gradle build` from this directory with Java 21 and internet access. The output mod jar is in `build/libs/`.

## Current limits

This is an early runtime milestone, not a full client. It does not implement combat automation, player targeting, hidden-player tracking, or anti-cheat bypasses. The HUD has not yet been tested in a live Minecraft session. The branded startup/loading overlay is **not implemented yet**. Launcher-to-game integration, additional module types, more granular layout controls, and support for other Minecraft versions remain future work.
