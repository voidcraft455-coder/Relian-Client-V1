# Relian Client Fabric runtime

Early in-game client runtime targeting Minecraft Java **1.21.1 + Fabric**.

## HUD editor

Press **Right Shift** to open the HUD editor. The editor lets you toggle modules, select a module, cycle its color, adjust scale, and drag its preview position. Settings persist in `config/relian-hud.json`.

## HUD modules

- FPS counter
- Coordinates
- Keystrokes
- Armor status and durability
- Ping display
- Held-item durability
- Active potion effects and remaining duration
- Facing direction
- Biome name
- Movement speed
- Local clock
- Dimension
- Target block
- Health
- Hunger and saturation
- World time and day count
- Server address and player count
- Session timer

Each module has an enable/disable setting, screen position, scale, and color.

## Build

Use Java 21 and run `gradle build` in this directory. The mod JAR is produced in `build/libs/`.

## Current limits

This is an early runtime milestone. It currently targets Fabric 1.21.1 and is distributed separately from the Windows launcher. The launcher does not yet install this runtime automatically. The expanded HUD code has passed the repository's Fabric CI build, but the modules still need testing in a live Minecraft session.

The branded startup overlay also needs in-game verification. This runtime does not include combat automation, auto-aim, fast-crystal automation, hidden-player tracking, or anti-cheat bypasses.
