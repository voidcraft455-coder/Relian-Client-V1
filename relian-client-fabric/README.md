# Relian Client Fabric runtime

This is the early in-game runtime for Minecraft Java **1.21.1 + Fabric**, separate from the JavaFX launcher until the game installation and launch pipeline is complete.

## HUD editor

Press **Right Shift** to open the HUD editor. The editor uses rounded panels and controls, and lets you toggle modules, select a module, cycle its color, adjust scale, and drag its preview position. Settings persist in `config/relian-hud.json`.

Current HUD modules:
- FPS counter
- Coordinates
- Keystrokes
- Armor durability
- Ping display
- Held-item durability
- Active potion effects and remaining duration
- Facing direction
- Biome name

The in-game HUD uses rounded translucent backgrounds. This is an original Relian visual design, not Lunar Client or Dawn Client artwork.

## Build

Use Java 21 and run `gradle build` in this directory. The mod jar is produced in `build/libs/`.

## Current limits

This is an early runtime milestone, not a complete client. It does not include combat automation, auto-aim, fast-crystal automation, player targeting, hidden-player tracking, or anti-cheat bypasses. The branded startup overlay has source code but has not yet been verified in a live Minecraft session. The runtime is not yet connected to the launcher's game installation and launch pipeline, and only targets 1.21.1 at present.
